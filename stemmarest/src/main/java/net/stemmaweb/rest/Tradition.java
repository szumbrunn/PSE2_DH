package net.stemmaweb.rest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLStreamException;

import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TextInfoModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.services.Neo4JToDotParser;
import net.stemmaweb.services.Neo4JToGraphMLParser;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Uniqueness;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

/**
 * 
 * @author ramona, sevi, joel
 *
 **/

@Path("/tradition")
public class Tradition implements IResource {

	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();


	/**
	 * 
	 * @param textInfo
	 *            in JSON Format
	 * @return OK on success or an ERROR as JSON
	 */
	@POST
	@Path("{textId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response changeOwnerOfATradition(TextInfoModel textInfo, @PathParam("textId") String textId) {


		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		if (!DatabaseService.checkIfUserExists(textInfo.getOwnerId(),db)) {
			return Response.status(Response.Status.NOT_FOUND).entity("Error: A user with this id does not exist")
					.build();
		}


		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (textId:TRADITION {id:'" + textId + "'}) return textId");
			Iterator<Node> nodes = result.columnAs("textId");

			if (nodes.hasNext()) {
				// Remove the old ownership
				String removeRelationQuery = "MATCH (tradition:TRADITION {id: '" + textId + "'}) "
						+ "MATCH tradition<-[r:NORMAL]-(:USER) DELETE r";
				result = engine.execute(removeRelationQuery);
				System.out.println(result.dumpToString());

				// Add the new ownership
				String createNewRelationQuery = "MATCH(user:USER {id:'" + textInfo.getOwnerId() + "'}) "
						+ "MATCH(tradition: TRADITION {id:'" + textId + "'}) " + "SET tradition.name = '"
						+ textInfo.getName() + "' " + "SET tradition.public = '" + textInfo.getIsPublic() + "' "
						+ "CREATE (tradition)<-[r:NORMAL]-(user) RETURN r, tradition";
				result = engine.execute(createNewRelationQuery);
				System.out.println(result.dumpToString());

			} else {
				// Tradition no found
				return Response.status(Response.Status.NOT_FOUND).entity("Tradition not found").build();
			}

			tx.success();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
		} finally {
			db.shutdown();
		}
		return Response.status(Response.Status.OK).entity(textInfo).build();
	}
	
	@GET
	@Path("all")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllTraditions()
	{
		List<TraditionModel> traditionList = new ArrayList<TraditionModel>();
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		ExecutionEngine engine = new ExecutionEngine(db);
		
		try (Transaction tx = db.beginTx()) 
		{
			ExecutionResult result = engine.execute("match (u:USER)-[:NORMAL]->(n:TRADITION) return n");
			Iterator<Node> traditions = result.columnAs("n");
			while(traditions.hasNext())
			{
				Node trad = traditions.next();
				TraditionModel tradModel = new TraditionModel();
				if(trad.hasProperty("id"))
					tradModel.setId(trad.getProperty("id").toString());
				if(trad.hasProperty("dg1"))	
					tradModel.setName(trad.getProperty("dg1").toString());
				traditionList.add(tradModel);
			}
			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
			db.shutdown();
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		} finally {
			db.shutdown();
		}
		
		return Response.ok().entity(traditionList).build();
	}

	@GET
	@Path("witness/{tradId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllWitnesses(@PathParam("tradId") String tradId) {

		ArrayList<WitnessModel> witlist = new ArrayList<WitnessModel>();

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		ExecutionEngine engine = new ExecutionEngine(db);

		try (Transaction tx = db.beginTx()) {
			Node traditionNode = null;
			Iterable<Relationship> relationships = null;
			Node startNode = null;

			try {
				traditionNode = getTraditionNode(tradId, engine);
				relationships = getRelationships(traditionNode);								
			
			if (traditionNode == null){
				return Response.status(Status.NOT_FOUND).entity("tradition not found").build();
			}

			if (relationships == null){
				return Response.status(Status.NOT_FOUND).entity("relationships not found").build();
			}

			startNode = null;
				startNode = DatabaseService.getStartNode(tradId, db);
				if (startNode == null)
				return Response.status(Status.NOT_FOUND).entity("no tradition with this id was found").build();

			relationships = startNode.getRelationships(Direction.OUTGOING);

			if (relationships == null)
				return Response.status(Status.NOT_FOUND).entity("start node not found").build();

			for (Relationship rel : relationships) {
				for (String id : ((String[]) rel.getProperty("lexemes"))) {
					WitnessModel witM = new WitnessModel();
					witM.setId(id);

					witlist.add(witM);
				}
			}

			tx.success();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			db.shutdown();
		}

		return Response.ok(witlist).build();
	}
	}

	@GET
	@Path("relation/{tradId}/relationships")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllRelationships(@PathParam("tradId") String tradId) {

		ArrayList<RelationshipModel> relList = new ArrayList<RelationshipModel>();

		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		ExecutionEngine engine = new ExecutionEngine(db);

		try (Transaction tx = db.beginTx()) {

			ExecutionResult result = engine.execute("match (n:TRADITION {id:'"+ tradId +"'})-[:NORMAL]->(s:WORD) return s");
			Iterator<Node> nodes = result.columnAs("s");
			Node startNode = nodes.next();
			
			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL,Direction.OUTGOING)
					.uniqueness(Uniqueness.NODE_GLOBAL)
					.traverse(startNode).nodes()) {
				
				Iterable<Relationship> rels = node.getRelationships(ERelations.RELATIONSHIP,Direction.OUTGOING);
				for(Relationship rel : rels)
				{
					RelationshipModel relMod = new RelationshipModel();
	
					if (rel.getStartNode() != null)
						relMod.setSource(String.valueOf(rel.getStartNode().getId()));
					if (rel.getEndNode() != null)
						relMod.setTarget(String.valueOf(rel.getEndNode().getId()));
					relMod.setId(String.valueOf(rel.getId()));
					if (rel.hasProperty("de0"))
						relMod.setDe0(rel.getProperty("de0").toString());
					if (rel.hasProperty("de1"))
						relMod.setDe1(rel.getProperty("de1").toString());
					if (rel.hasProperty("de2"))
						relMod.setDe2(rel.getProperty("de2").toString());
					if (rel.hasProperty("de3"))
						relMod.setDe3(rel.getProperty("de3").toString());
					if (rel.hasProperty("de4"))
						relMod.setDe4(rel.getProperty("de4").toString());
					if (rel.hasProperty("de5"))
						relMod.setDe5(rel.getProperty("de5").toString());
					if (rel.hasProperty("de6"))
						relMod.setDe6(rel.getProperty("de6").toString());
					if (rel.hasProperty("de7"))
						relMod.setDe7(rel.getProperty("de7").toString());
					if (rel.hasProperty("de8"))
						relMod.setDe8(rel.getProperty("de8").toString());
					if (rel.hasProperty("de9"))
						relMod.setDe9(rel.getProperty("de9").toString());
					if (rel.hasProperty("de10"))
						relMod.setDe10(rel.getProperty("de10").toString());
					if (rel.hasProperty("de11"))
						relMod.setDe11(rel.getProperty("de11").toString());
					if (rel.hasProperty("de12"))
						relMod.setDe12(rel.getProperty("de12").toString());
	
					relList.add(relMod);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			db.shutdown();
		}
		return Response.ok(relList).build();
	}

	/**
	 * Helper method for getting all outgoing relationships of a node
	 * 
	 * @param traditionNode
	 * @return
	 * @throws DataBaseException
	 */
	private Iterable<Relationship> getRelationships(Node traditionNode){
		Iterable<Relationship> relations = traditionNode.getRelationships(Direction.OUTGOING);		
		return relations;
	}

	/**
	 * Helper method for getting the tradition node with a given tradition id
	 * 
	 * @param tradId
	 * @param engine
	 * @return
	 * @throws DataBaseException
	 */
	private Node getTraditionNode(String tradId, ExecutionEngine engine) {
		ExecutionResult result = engine.execute("match (n:TRADITION {id: '" + tradId + "'}) return n");
		Iterator<Node> nodes = result.columnAs("n");

		if (!nodes.hasNext())
			return null;
		return nodes.next();
	}

	/**
	 * Returns GraphML file from specified tradition owned by user
	 * 
	 * @param userId
	 * @param traditionName
	 * @return XML data
	 */
	@GET
	@Path("get/{tradId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getTradition(@PathParam("tradId") String tradId) {
		Neo4JToGraphMLParser parser = new Neo4JToGraphMLParser();
		return parser.parseNeo4J(tradId);
	}
	
	/**
	 * Removes a complete tradition
	 * @param tradId
	 * @return
	 */
	@DELETE
	@Path("{tradId}")
	public Response deleteUserById(@PathParam("tradId") String tradId) {
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);

		ExecutionEngine engine = new ExecutionEngine(db);
		try (Transaction tx = db.beginTx()) {
			ExecutionResult result = engine.execute("match (tradId:TRADITION {id:'" + tradId + "'}) return userId");
			Iterator<Node> nodes = result.columnAs("tradId");

			if (nodes.hasNext()) {
				Node node = nodes.next();
				
				/*
				 * Find all the nodes and relations to remove
				 */
				Set<Relationship> removableRelations = new HashSet<Relationship>();
				Set<Node> removableNodes = new HashSet<Node>();
				for (Node currentNode : db.traversalDescription()
				        .depthFirst()
				        .relationships( ERelations.NORMAL, Direction.OUTGOING)
				        .relationships( ERelations.STEMMA, Direction.OUTGOING)
				        .relationships( ERelations.RELATIONSHIP, Direction.OUTGOING)
				        .uniqueness( Uniqueness.RELATIONSHIP_GLOBAL )
				        .traverse( node )
				        .nodes()) 
				{
					for(Relationship currentRelationship : currentNode.getRelationships()){
						removableRelations.add(currentRelationship);
					}
					removableNodes.add(currentNode);
				}
				
				/*
				 * Remove the nodes and relations
				 */
				for(Relationship removableRel:removableRelations){
		            removableRel.delete();
		        }
				for(Node remNode:removableNodes){
		            remNode.delete();
		        }
			} else {
				return Response.status(Response.Status.NOT_FOUND).entity("A tradition with this id was not found!").build();
			}

			tx.success();
		} finally {
			db.shutdown();
		}
		return Response.status(Response.Status.OK).build();
	}

	/**
	 * Imports a tradition by given GraphML file and meta data
	 *
	 * @return String that will be returned as a text/plain response.
	 * @throws XMLStreamException
	 */
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	@Path("new")
	public Response importGraphMl(@FormDataParam("name") String name, @FormDataParam("language") String language,
			@FormDataParam("public") String is_public, @FormDataParam("userId") String userId,
			@FormDataParam("file") InputStream uploadedInputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail) throws IOException, XMLStreamException {

		User user = new User();
		if (!user.checkUserExists(userId)) {
			return Response.status(Response.Status.CONFLICT).entity("Error: No user with this id exists").build();
		}

		// Boolean is_public_bool = is_public.equals("on")? true : false;
		String uploadedFileLocation = "upload/" + fileDetail.getFileName();

		// save it
		writeToFile(uploadedInputStream, uploadedFileLocation);

		GraphMLToNeo4JParser parser = new GraphMLToNeo4JParser();
		Response resp = parser.parseGraphML(uploadedFileLocation, userId);
		// The prefix will always be some sort of '12_', to make sure that all
		// nodes are unique

		deleteFile(uploadedFileLocation);

		return resp;
	}

	/**
	 * Helper method for writing stream into a given location
	 * 
	 * @param uploadedInputStream
	 * @param uploadedFileLocation
	 */
	private void writeToFile(InputStream uploadedInputStream, String uploadedFileLocation) {

		try {
			OutputStream out = new FileOutputStream(new File(uploadedFileLocation));
			int read = 0;
			byte[] bytes = new byte[1024];

			out = new FileOutputStream(new File(uploadedFileLocation));
			while ((read = uploadedInputStream.read(bytes)) != -1) {
				out.write(bytes, 0, read);
			}
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

		/**
	 * Helper method for deleting a file by given name
	 * 
	 * @param filename
	 */
	private void deleteFile(String filename) {
		File file = new File(filename);
		file.delete();
	}

	/**
	 * Returns DOT file from specified tradition owned by user
	 * 
	 * @param traditionName
	 * @return XML data
	 */
	@GET
	@Path("getdot/{tradId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getDot(@PathParam("tradId") String tradId) {
		
		GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
		Neo4JToDotParser parser = new Neo4JToDotParser(db);
		Response resp = parser.parseNeo4J(tradId);
		
		String filename = "upload/" + "output.dot";
		
		String everything = "";
		try(BufferedReader br = new BufferedReader(new FileReader(filename))) {
	        StringBuilder sb = new StringBuilder();
	        String line = br.readLine();

	        while (line != null) {
	            sb.append(line);
	            sb.append(System.lineSeparator());
	            line = br.readLine();
	        }
	        everything = sb.toString();
	    } catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return Response.ok(everything).build();
	}
}
