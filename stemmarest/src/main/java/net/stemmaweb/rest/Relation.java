package net.stemmaweb.rest;

import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import net.stemmaweb.model.RelationshipModel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;


/**
 * 
 * @author jakob
 *
 */
@Path("/relation")
public class Relation implements IResource {
	GraphDatabaseFactory dbFactory = new GraphDatabaseFactory();
	
	/**
	 * 
	 * @return string
	 */
    @GET 
    @Produces("text/plain")
    public String getIt() {
        return "The relation api is up and running";
    }
    
    /**
     * 
     * @param relationshipModel
     * @param textId
     * @return
     */
    @POST
    @Path("{textId}/relationships")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
	public Response create(RelationshipModel relationshipModel,
			@PathParam("textId") String textId) {
    	
    	GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
    	Relationship relationshipAtoB = null;
    	try (Transaction tx = db.beginTx()) 
    	{
        	
    		/*
    		 * Currently search by id search because is much faster by measurement. Because 
    		 * the id search is O(n) just go through all ids without care. And the 
    		 * 
    		 */
    		System.out.println(relationshipModel.getSource());
    		Node readingA = db.getNodeById(Long.parseLong(relationshipModel.getSource()));
    		Node readingB = db.getNodeById(Long.parseLong(relationshipModel.getTarget()));
    		
        	relationshipAtoB = readingA.createRelationshipTo(readingB, ERelations.RELATIONSHIP);
        	relationshipAtoB.setProperty("de11", nullToEmptyString(relationshipModel.getDe11()));
//        	relationshipAtoB.setProperty("de0", nullToEmptyString(relationshipModel.getDe0()));
        	relationshipAtoB.setProperty("de1", nullToEmptyString(relationshipModel.getDe1()));
//        	relationshipAtoB.setProperty("de3", nullToEmptyString(relationshipModel.getDe3()));
//        	relationshipAtoB.setProperty("de4", nullToEmptyString(relationshipModel.getDe5()));
        	relationshipAtoB.setProperty("de6", nullToEmptyString(relationshipModel.getDe6()));
//        	relationshipAtoB.setProperty("de7", nullToEmptyString(relationshipModel.getDe7()));
        	relationshipAtoB.setProperty("de8", nullToEmptyString(relationshipModel.getDe8()));
        	relationshipAtoB.setProperty("de9", nullToEmptyString(relationshipModel.getDe9()));
        	relationshipAtoB.setProperty("de10", nullToEmptyString(relationshipModel.getDe10()));
        	
        	tx.success();
    	} 
    	catch (NotFoundException nfe) 
    	{
    		return Response.status(Response.Status.NOT_FOUND).tag(nfe.toString()).build();
    	}
    	catch (Exception e)
    	{
    		return Response.serverError().build();
    	} finally {	
    		db.shutdown();
    	}

		return Response.status(Response.Status.CREATED).entity("{\"id\":\""+relationshipAtoB.getId()+"\"}").build();
	}
    
    
    /***
     *TODO needs clarification 
     *
     *DELETE Does not suport parameters according to RFC2616 9.7. So the method is here implemented 
     *with post and {textId}/relationships/delete 
     *source and target node as parameters like in https://github.com/tla/stemmaweb/blob/master/lib/stemmaweb/Controller/Relation.pm line 271
     */
    /**
     * Remove all like https://github.com/tla/stemmaweb/blob/master/lib/stemmaweb/Controller/Relation.pm line 271)
     *  in Relationships of type RELATIONSHIP between the two nodes.
     * @param relationshipModel
     * @param textId
     * @return HTTP Response 404 when no node was found, 200 When relationships where removed
     */
    //@DELETE 
    @POST
    @Path("{textId}/relationships/delete")
    @Consumes(MediaType.APPLICATION_JSON)
	public Response delete(RelationshipModel relationshipModel,
			@PathParam("textId") String textId) {

    	GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
    	try (Transaction tx = db.beginTx()) 
    	{
        	
    		Node readingA = db.getNodeById(Long.parseLong(relationshipModel.getSource()));
    		Node readingB = db.getNodeById(Long.parseLong(relationshipModel.getTarget()));
    		
    		Iterable<Relationship> relationships = readingA.getRelationships(ERelations.RELATIONSHIP);

    		Relationship relationshipAtoB = null;
    		for (Relationship relationship : relationships) {
    			if((relationship.getStartNode().equals(readingA)||relationship.getEndNode().equals(readingA)) &&
    					relationship.getStartNode().equals(readingB)||relationship.getEndNode().equals(readingB)){
    				relationshipAtoB = relationship;
    			}
			}
    		
    		if(relationshipAtoB != null)
    			relationshipAtoB.delete();
    		else 
    			return Response.status(Response.Status.NOT_FOUND).build();
    			
        	tx.success();
    	} catch (Exception e){
    		System.out.println(e.toString());
    		return Response.serverError().build();
    	} finally {	
    		db.shutdown();
    	}

		return Response.status(Response.Status.OK).build();
	}
    
    /**
     * Removes a relationship by ID
     * @param relationshipId
     * @param textId
     * @return HTTP Response 404 when no Relationship was found with id, 200 when the Relationship was removed
     */
    @DELETE
    @Path("{textId}/relationships/{relationshipId}")
    public Response deleteById(@PathParam("relationshipId") String relationshipId,
			@PathParam("textId") String textId) {
    	
    	GraphDatabaseService db = dbFactory.newEmbeddedDatabase(DB_PATH);
    	try (Transaction tx = db.beginTx()) 
    	{
    		Relationship relationship = db.getRelationshipById(Long.parseLong(relationshipId));
    		relationship.delete();
    		tx.success();
    	} catch (NotFoundException e) {
    		return Response.status(Response.Status.NOT_FOUND).build();
    	}
    	finally {
    		db.shutdown();
    	}
    	return Response.ok().build();
    }
    
    private String nullToEmptyString(String str){
    	if(str == null)
    		return "";
    	else 
    		return str;
    }
}