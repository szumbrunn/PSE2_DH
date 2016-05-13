package net.stemmaweb.rest;

import java.util.ArrayList;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Uniqueness;

/**
 * Comprises all the API calls related to a witness.
 * Can be called using http://BASE_URL/witness
 * @author PSE FS 2015 Team2
 */

@Path("/witness")
public class Witness implements IResource {
	
	GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
	GraphDatabaseService db = dbServiceProvider.getDatabase();

	/**
	 * finds a witness in the database and returns it as a string
	 * 
	 * @param userId
	 *            : the id of the user who owns the witness
	 * @param traditionName
	 *            : the name of the tradition which the witness is in
	 * @param witnessId
	 *            : the id of the witness
	 * @return a witness as a string
	 */
	@GET
	@Path("gettext/fromtradition/{tradId}/ofwitness/{witnessId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getWitnessAsText(@PathParam("tradId") String tradId,
			@PathParam("witnessId") String witnessId) {
		
		String witnessAsText = "";
		final String WITNESS_ID = witnessId;
		Node startNode = DatabaseService.getStartNode(tradId, db);
		Evaluator e = Witness.getEvalForWitness(WITNESS_ID);

		try (Transaction tx = db.beginTx()) {
			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING)
					.evaluator(e).uniqueness(Uniqueness.RELATIONSHIP_PATH)
					.traverse(startNode).nodes()) {
				if (!node.getProperty("text").equals("#END#"))
					witnessAsText += (String) node.getProperty("text") + " ";
			}
			tx.success();
		} catch (Exception exception) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		if (witnessAsText.equals(""))
			return Response.status(Status.NOT_FOUND)
					.entity("no witness with this id was found").build();
		return Response.status(Response.Status.OK)
				.entity("{\"text\":\"" + witnessAsText.trim() + "\"}").build();
	}

	/**
	 * find a requested witness in the data base and return it as a string
	 * according to define start and end readings (including the readings in
	 * those ranks). if end-rank is too high or start-rank too low will return
	 * till the end/from the start of the witness
	 * 
	 * @param userId
	 *            : the id of the user who owns the witness
	 * @param traditionName
	 *            : the name of the tradition which the witness is in
	 * @param witnessId
	 *            : the id of the witness
	 * @return a witness as a string
	 */
	@GET
	@Path("gettext/fromtradition/{tradId}/ofwitness/{witnessId}/fromstartrank/{startRank}/toendrank/{endRank}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getWitnessAsTextBetweenRanks(
			@PathParam("tradId") String tradId,
			@PathParam("witnessId") String witnessId,
			@PathParam("startRank") String startRankAsString,
			@PathParam("endRank") String endRankAsString) {
		
		String witnessAsText = "";
		final String WITNESS_ID = witnessId;
		long startRank = Long.parseLong(startRankAsString);
		long endRank = Long.parseLong(endRankAsString);
		if (endRank < startRank)
			swapRanks(startRank, endRank);
		if (endRank == startRank)
			return Response.status(Status.INTERNAL_SERVER_ERROR)
					.entity("end-rank is equal to start-rank").build();

		Evaluator e = Witness.getEvalForWitness(WITNESS_ID);
		Node startNode = DatabaseService.getStartNode(tradId, db);

		try (Transaction tx = db.beginTx()) {
			for (Node node : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING)
					.evaluator(e).uniqueness(Uniqueness.RELATIONSHIP_PATH)
					.traverse(startNode).nodes()) {
				long nodeRank = Long.parseLong( node.getProperty("rank").toString());
				if (nodeRank >= startRank && nodeRank <= endRank) {
					if (!node.getProperty("text").equals("#END#"))
						witnessAsText += (String) node.getProperty("text")
								+ " ";
				}
			}
			tx.success();
		} catch (Exception exception) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}
		if (witnessAsText.equals(""))
			return Response.status(Status.NOT_FOUND)
					.entity("no witness with this id was found").build();
		return Response.status(Response.Status.OK)
				.entity("{\"text\":\"" + witnessAsText.trim() + "\"}").build();

	}

	private void swapRanks(long startRank, long endRank) {
		long tempRank = endRank;
		endRank = startRank;
		startRank = tempRank;
	}

	/**
	 * finds a witness in the database and returns it as a list of readings
	 * 
	 * @param userId
	 *            : the id of the user who owns the witness
	 * @param traditionName
	 *            : the name of the tradition which the witness is in
	 * @param witnessId
	 *            : the id of the witness
	 * @return a witness as a list of models of readings in json format
	 */
	@GET
	@Path("getreadinglist/fromtradition/{tradId}/ofwitness/{witnessId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getWitnessAsReadings(@PathParam("tradId") String tradId,
			@PathParam("witnessId") String witnessId) {
		final String WITNESS_ID = witnessId;

		ArrayList<ReadingModel> readingModels = new ArrayList<ReadingModel>();

		Node startNode = DatabaseService.getStartNode(tradId, db);
		if (startNode == null)
			return Response.status(Status.NOT_FOUND)
					.entity("Could not find tradition with this id").build();
		
		Evaluator e = Witness.getEvalForWitness(WITNESS_ID);

		try (Transaction tx = db.beginTx()) {			

			for (Node startNodes : db.traversalDescription().depthFirst()
					.relationships(ERelations.NORMAL, Direction.OUTGOING)
					.evaluator(e).uniqueness(Uniqueness.RELATIONSHIP_GLOBAL)
					.traverse(startNode).nodes()) {
				ReadingModel tempReading = new ReadingModel(startNodes);

				readingModels.add(tempReading);
			}
			tx.success();
		} catch (Exception exception) {
			return Response.status(Status.INTERNAL_SERVER_ERROR).build();
		}	
		if (readingModels.size() == 0)
			return Response.status(Status.NOT_FOUND)
					.entity("no witness with this id was found").build();
		if (readingModels.get(readingModels.size() - 1).getText()
				.equals("#END#"))
			readingModels.remove(readingModels.size() - 1);		
		return Response.status(Status.OK).entity(readingModels).build();
	}

	public static Evaluator getEvalForWitness(final String WITNESS_ID) {
		Evaluator e = new Evaluator() {
			@Override
			public Evaluation evaluate(org.neo4j.graphdb.Path path) {
	
				if (path.length() == 0)
					return Evaluation.EXCLUDE_AND_CONTINUE;
	
				boolean includes = false;
				boolean continues = false;
	
				if (path.lastRelationship().hasProperty("witnesses")) {
					String[] arr = (String[]) path.lastRelationship()
							.getProperty("witnesses");
					for (String str : arr) {
						if (str.equals(WITNESS_ID)) {
							includes = true;
							continues = true;
						}
					}
				}
				return Evaluation.of(includes, continues);
			}
		};
		return e;
	}
}
