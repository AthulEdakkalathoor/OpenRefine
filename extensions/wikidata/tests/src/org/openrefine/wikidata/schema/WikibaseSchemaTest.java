package org.openrefine.wikidata.schema;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.openrefine.wikidata.testing.TestingDataGenerator;
import org.openrefine.wikidata.updates.ItemUpdate;
import org.openrefine.wikidata.updates.ItemUpdateBuilder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.interfaces.Claim;
import org.wikidata.wdtk.datamodel.interfaces.ItemIdValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Snak;
import org.wikidata.wdtk.datamodel.interfaces.SnakGroup;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.StatementRank;
import org.wikidata.wdtk.datamodel.interfaces.StringValue;
import org.wikidata.wdtk.datamodel.interfaces.TimeValue;

import com.google.refine.browsing.Engine;
import com.google.refine.model.Project;
import com.google.refine.tests.RefineTest;
import com.google.refine.util.ParsingUtilities;

public class WikibaseSchemaTest extends RefineTest {
    
    private ItemIdValue qid1 = Datamodel.makeWikidataItemIdValue("Q1377");
    private ItemIdValue qid2 = Datamodel.makeWikidataItemIdValue("Q865528");
    private TimeValue date1 = Datamodel.makeTimeValue(1919, (byte)1, (byte)1,
            (byte)0, (byte)0, (byte)0, (byte)9, (byte)0, (byte)1, (byte)0, TimeValue.CM_GREGORIAN_PRO);
    private TimeValue date2 = Datamodel.makeTimeValue(1965, (byte)1, (byte)1,
            (byte)0, (byte)0, (byte)0, (byte)9, (byte)0, (byte)1, (byte)0, TimeValue.CM_GREGORIAN_PRO);
    private StringValue url = Datamodel.makeStringValue("http://www.ljubljana-slovenia.com/university-ljubljana");
    private PropertyIdValue inceptionPid = Datamodel.makeWikidataPropertyIdValue("P571");
    private PropertyIdValue refPid = Datamodel.makeWikidataPropertyIdValue("P854");
    private PropertyIdValue retrievedPid = Datamodel.makeWikidataPropertyIdValue("P813");
    private Snak refSnak = Datamodel.makeValueSnak(refPid, url);
    private Snak retrievedSnak = Datamodel.makeValueSnak(retrievedPid,
            Datamodel.makeTimeValue(2018, (byte) 2, (byte) 28, TimeValue.CM_GREGORIAN_PRO));
    private Snak mainSnak1 = Datamodel.makeValueSnak(inceptionPid, date1);
    private Snak mainSnak2 = Datamodel.makeValueSnak(inceptionPid, date2);
    private Claim claim1 = Datamodel.makeClaim(qid1, mainSnak1, Collections.emptyList());
    private Claim claim2 = Datamodel.makeClaim(qid2, mainSnak2, Collections.emptyList());
    private SnakGroup refSnakGroup =  Datamodel.makeSnakGroup(Collections.singletonList(refSnak));
    private SnakGroup retrievedSnakGroup = Datamodel.makeSnakGroup(Collections.singletonList(retrievedSnak));
    private Statement statement1 = Datamodel.makeStatement(claim1,
            Collections.singletonList(Datamodel.makeReference(Arrays.asList(refSnakGroup, retrievedSnakGroup))),
            StatementRank.NORMAL, "");
    private Statement statement2 = Datamodel.makeStatement(claim2,
            Collections.singletonList(Datamodel.makeReference(Collections.singletonList(retrievedSnakGroup))),
            StatementRank.NORMAL, "");
    
    private Project project;
    
    static JSONObject jsonFromFile(String filename) throws IOException, JSONException {
        byte[] contents = Files.readAllBytes(Paths.get(filename));
        String decoded = new String(contents, "utf-8");
        return ParsingUtilities.evaluateJsonStringToObject(decoded);
    }
    
    @BeforeMethod
    public void setUpProject() {
        project = this.createCSVProject(
                "subject,inception,reference\n"+
                "Q1377,1919,http://www.ljubljana-slovenia.com/university-ljubljana\n"+
                "Q865528,1965,");
        project.rows.get(0).cells.set(0, TestingDataGenerator.makeMatchedCell("Q1377", "University of Ljubljana"));
        project.rows.get(1).cells.set(0, TestingDataGenerator.makeMatchedCell("Q865528", "University of Warwick"));
    }
    
    @Test
    public void testSerialize() throws JSONException, IOException {
        JSONObject serialized = jsonFromFile("data/schema/history_of_medicine.json");
        WikibaseSchema parsed = WikibaseSchema.reconstruct(serialized);
        StringWriter writer = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(writer);
        parsed.write(jsonWriter, new Properties());
        writer.close();
        JSONObject newSerialized = ParsingUtilities.evaluateJsonStringToObject(writer.toString());
        // toString because it looks like JSONObject equality isn't great…
        assertEquals(jsonFromFile("data/schema/history_of_medicine_normalized.json").toString(), newSerialized.toString());
    }
    
    @Test
    public void testDeserialize() throws JSONException, IOException {
        // this json file was generated by an earlier version of the software
        // it contains extra "type" fields that are now ignored.
        JSONObject serialized = jsonFromFile("data/schema/roarmap.json");
        WikibaseSchema.reconstruct(serialized); 
    }

    @Test
    public void testEvaluate() throws JSONException, IOException {
        JSONObject serialized = jsonFromFile("data/schema/inception.json");
        WikibaseSchema schema = WikibaseSchema.reconstruct(serialized);
        Engine engine = new Engine(project);
        List<ItemUpdate> updates = schema.evaluate(project, engine);
        List<ItemUpdate> expected = new ArrayList<>();
        ItemUpdate update1 = new ItemUpdateBuilder(qid1).addStatement(statement1).build();
        expected.add(update1);
        ItemUpdate update2 = new ItemUpdateBuilder(qid2).addStatement(statement2).build();
        expected.add(update2);
        assertEquals(expected, updates);
    }
    
    @Test
    public void testEvaluateRespectsFacets() throws JSONException, IOException {
        JSONObject serialized = jsonFromFile("data/schema/inception.json");
        WikibaseSchema schema = WikibaseSchema.reconstruct(serialized);
        Engine engine = new Engine(project);
        JSONObject engineConfig = new JSONObject("{\n" + 
                "      \"mode\": \"row-based\",\n" + 
                "      \"facets\": [\n" + 
                "        {\n" + 
                "          \"mode\": \"text\",\n" + 
                "          \"invert\": false,\n" + 
                "          \"caseSensitive\": false,\n" + 
                "          \"query\": \"www\",\n" + 
                "          \"name\": \"reference\",\n" + 
                "          \"type\": \"text\",\n" + 
                "          \"columnName\": \"reference\"\n" + 
                "        }\n" + 
                "      ]\n" + 
                "    }");
        engine.initializeFromJSON(engineConfig);
        List<ItemUpdate> updates = schema.evaluate(project, engine);
        List<ItemUpdate> expected = new ArrayList<>();
        ItemUpdate update1 = new ItemUpdateBuilder(qid1).addStatement(statement1).build();
        expected.add(update1);
        assertEquals(expected, updates);
    }
}
