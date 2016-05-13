package net.stemmaweb.services;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.IResource;
import net.stemmaweb.rest.Nodes;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

/**
 * This class provides methods for exporting Dot File from Neo4J
 * @author PSE FS 2015 Team2
 */
public class DotToNeo4JParser implements IResource
{
	GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
	GraphDatabaseService db = dbServiceProvider.getDatabase();
	String dot = "";
	List<Node> nodes = new ArrayList<Node>();

	public DotToNeo4JParser(GraphDatabaseService db){
		this.db = db;
	}
	
	public Response parseDot(String dot, String tradId)
	{	
		this.dot = dot;
    	
    	try (Transaction tx = db.beginTx()) 
    	{
    		while(nextObject(tradId));
    		if(nodes.size()==0)
    			return Response.status(Status.NOT_FOUND).build();
    		else
    			nodes.get(0).createRelationshipTo(nodes.get(1), ERelations.STEMMA);
    		tx.success();
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    		return Response.status(Status.NOT_FOUND).build();
    	}
		return Response.ok(dot).build();
	}
	
	/**
	 * parses a dot into neo4j, object for object, returns false when no more objects exist
	 * adds all nodes below tradId
	 * @return
	 */
	private boolean nextObject(String tradId)
	{
		// check either graph or node/edge is next
		int i;
		if(((dot.indexOf('{')==-1)&&(dot.indexOf(';')!=-1))
				|| (dot.indexOf(';')<dot.indexOf('{')))
		{
			i = dot.indexOf(';');
			String tmp = dot.substring(0, i);
			boolean undirected = false;
			if((undirected = tmp.contains("--")) || tmp.contains("-&gt;") || tmp.contains("->"))
			{
				String[] splitted;
				if(undirected)
				{
					splitted = tmp.split("--");
					splitted[0] = splitted[0].replaceAll(" ", "");
					splitted[1] = splitted[1].replaceAll(" ", "");
					Node source = findNodeById(splitted[0]);
					Node target = findNodeById(splitted[1]);
					if(source!=null && target!=null)
						source.createRelationshipTo(target, ERelations.STEMMA);
				}
				else
				{
					if(tmp.contains("->"))
						splitted = tmp.split("->");
					else
						splitted = tmp.split("-&gt;");
					splitted[0] = splitted[0].replaceAll(" ", "");
					splitted[1] = splitted[1].replaceAll(" ", "");
					Node source = findNodeById(splitted[0]);
					Node target = findNodeById(splitted[1]);
					if(source!=null && target!=null)
						source.createRelationshipTo(target, ERelations.STEMMA);
				}
			}
			else if(tmp.length()>0)
			{
				Node node = db.createNode(Nodes.WITNESS);
				if(dot.indexOf('[')!=-1)
				{
					String[] splitted = tmp.split("\\[");
					
					splitted[0] = splitted[0].replaceAll(" ", "");
					splitted[1] = splitted[1].replaceAll(" ", "");
					
					node.setProperty("id", splitted[0].trim());
					
					splitted[1] = splitted[1].replaceAll("\\[", "");
					splitted[1] = splitted[1].replaceAll("\\]", "");
					
					// replace this with the use of the correct delimiter of properties
					String[] sub = splitted[1].split(",");
					for(String str : sub)
					{
						String[] arr = str.split("=");
						node.setProperty(arr[0].trim(), arr[1].trim());
					}
					
					nodes.add(node);
				}
			}
			dot = dot.substring(i+1);
		}
		else if((i = dot.indexOf('{'))<dot.indexOf(';') && i>0)
		{ // this holds something like ' graph "stemma" ' or 'digraph "stem23423" '
			Node node = db.createNode(Nodes.STEMMA);
			String tmp = dot.substring(0, i);
			if(tmp.contains("digraph"))
			{
				node.setProperty("type", "digraph");
			}
			else if(tmp.contains("graph"))
			{
				node.setProperty("type", "graph");
			}
			String[] name = tmp.split("\"");
			if(name.length==3)
			{
				node.setProperty("name", name[1]);
			}
			
			Node trad = db.findNodesByLabelAndProperty(Nodes.TRADITION, "id", tradId).iterator().next();
			if(trad!=null)
				trad.createRelationshipTo(node, ERelations.STEMMA);
			nodes.add(node);
			dot = dot.substring(i+1);
		}
		else
		{
			return false;
		}
		return true;
	}
	
	private Node findNodeById(String id)
	{
		for(Node n : nodes)
		{
			if(n.hasProperty("id") && n.getProperty("id").equals(id))
				return n;
		}
		return null;
	}
}