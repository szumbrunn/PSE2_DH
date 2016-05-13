package net.stemmaweb.stemmaserver.benachmarktests;

import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.Iterator;

import net.stemmaweb.rest.Reading;
import net.stemmaweb.rest.Relation;
import net.stemmaweb.rest.Stemma;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.rest.User;
import net.stemmaweb.rest.Witness;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;
import net.stemmaweb.stemmaserver.OSDetector;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

import com.carrotsearch.junitbenchmarks.annotation.AxisRange;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
@AxisRange(min = 0, max = 0.2)
@BenchmarkMethodChart(filePrefix = "benchmark/benchmark-100kNodes")
public class Benchmark100kNodes extends BenachmarkTests {
	
	@BeforeClass
	public static void prepareTheDatabase(){

		RandomGraphGenerator rgg = new RandomGraphGenerator();

		GraphDatabaseServiceProvider.setImpermanentDatabase();
		GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
		
		GraphDatabaseService db = dbServiceProvider.getDatabase();
		
		
		userResource = new User();
		traditionResource = new Tradition();
		witnessResource = new Witness();
		readingResoruce = new Reading();
		relationResource = new Relation();
		importResource = new GraphMLToNeo4JParser();
		stemmaResource = new Stemma();
		
		jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
				.addResource(userResource)
				.addResource(traditionResource)
				.addResource(witnessResource)
				.addResource(relationResource)
				.addResource(readingResoruce)
				.addResource(stemmaResource).create();
		try {
			jerseyTest.setUp();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		rgg.role(db, 10, 10, 10, 100);

		
		if (OSDetector.isWin())
			filename = "src\\TestXMLFiles\\ReadingstestTradition.xml";
		else
			filename = "src/TestXMLFiles/ReadingstestTradition.xml";
		
		try {
			tradId = importResource.parseGraphML(filename, "1","Tradition").getEntity().toString().replace("{\"tradId\":", "").replace("}", "");
		} catch (FileNotFoundException f) {
			// this error should not occur
			assertTrue(false);
		}
		
		ExecutionEngine engine = new ExecutionEngine(db);
		ExecutionResult result = engine.execute("match (w:WORD {text:'showers'}) return w");
		Iterator<Node> nodes = result.columnAs("w");
		duplicateReadingNodeId = nodes.next().getId();

		result = engine.execute("match (w:WORD {text:'the root'}) return w");
		nodes = result.columnAs("w");
		theRoot = nodes.next().getId();
		
		result = engine.execute("match (w:WORD {text:'unto me'}) return w");
		nodes = result.columnAs("w");
		untoMe = nodes.next().getId();		
	}
	
	@AfterClass
	public static void shutdown() throws Exception{
		GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
		dbServiceProvider.getDatabase().shutdown();
		jerseyTest.tearDown();
	}
}
