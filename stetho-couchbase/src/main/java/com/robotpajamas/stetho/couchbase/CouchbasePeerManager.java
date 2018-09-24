package com.robotpajamas.stetho.couchbase;

import android.content.Context;
import android.util.Log;


import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.Document;
import com.couchbase.lite.Meta;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryBuilder;
import com.couchbase.lite.Result;
import com.couchbase.lite.ResultSet;
import com.couchbase.lite.SelectResult;
import com.facebook.stetho.inspector.console.CLog;
import com.facebook.stetho.inspector.helper.ChromePeerManager;
import com.facebook.stetho.inspector.helper.PeerRegistrationListener;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer;
import com.facebook.stetho.inspector.protocol.module.Console;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

// TODO: Check support for ForestDB
// TODO: See if opening/closing of managers/database can be optimized
class CouchbasePeerManager extends ChromePeerManager {

    private static final String DOC_PATTERN = "\"(.*?)\"";
    private static final List<String> COLUMN_NAMES = Arrays.asList("key", "value");

    private final Pattern mPattern = Pattern.compile(DOC_PATTERN);

    private final String mPackageName;
    private final Context mContext;
    private final boolean mShowMetadata;
    com.couchbase.lite.Database database;


    CouchbasePeerManager(Context context, String packageName, boolean showMetadata) {
        mContext = context;
        mPackageName = packageName;
        mShowMetadata = showMetadata;

        database = DatabaseManager.getSharedInstance(context).database;
        setListener(new PeerRegistrationListener() {
            @Override
            public void onPeerRegistered(JsonRpcPeer peer) {
                setupPeer(peer);
            }

            @Override
            public void onPeerUnregistered(JsonRpcPeer peer) {

            }
        });
    }

    private void setupPeer(JsonRpcPeer peer) {
        Database.DatabaseObject databaseParams = new Database.DatabaseObject();
        databaseParams.id = database.getName();
        databaseParams.name = database.getName();
        databaseParams.domain = mPackageName;
        databaseParams.version = "N/A";
        Database.AddDatabaseEvent eventParams = new Database.AddDatabaseEvent();
        eventParams.database = databaseParams;

        peer.invokeMethod("Database.addDatabase", eventParams, null /* callback */);
//        List<String> potentialDatabases = database.;
//        for (String database : potentialDatabases) {
//            Timber.d("Datebase Name: %s", database);
//            Database.DatabaseObject databaseParams = new Database.DatabaseObject();
//            databaseParams.id = database;
//            databaseParams.name = database;
//            databaseParams.domain = mPackageName;
//            databaseParams.version = "N/A";
//            Database.AddDatabaseEvent eventParams = new Database.AddDatabaseEvent();
//            eventParams.database = databaseParams;
//
//            peer.invokeMethod("Database.addDatabase", eventParams, null /* callback */);
//        }
    }

    List<String> getAllDocumentIds(String databaseId) {
        Timber.d("getAllDocumentIds: %s", databaseId);

        try {
            List<String> docIds = new ArrayList<>();
            Query query = QueryBuilder
                    .select(SelectResult.expression(Meta.id))
                    .from(DataSource.database(database));
            ResultSet result = query.execute();
            ListIterator<Result> resultItr = result.allResults().listIterator();
            while (resultItr.hasNext()){
                Result tempResult = resultItr.next();
                docIds.add(tempResult.getString(0));
            }
            Timber.d("getAllDocumentIds: %s", databaseId.toString());
            return docIds;
        } catch (Exception e) {
            return Collections.emptyList();
        } finally {

        }
    }

    Database.ExecuteSQLResponse executeSQL(String databaseId, String query) throws JSONException {
        Timber.d("executeSQL: %s, %s", databaseId, query);

        Database.ExecuteSQLResponse response = new Database.ExecuteSQLResponse();

        Matcher matcher = mPattern.matcher(query);
        if (!matcher.find()) {
            return response;
        }

        String docId = matcher.group(1);
        Timber.d("Parsed doc ID: %s", docId);

        Map<String, String> map = getDocument(databaseId, docId);
        response.columnNames = COLUMN_NAMES;
        response.values = new ArrayList<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            final String key = entry.getKey();
            if (!mShowMetadata && key.substring(0,1).equals("_")) {
                continue;
            }
            response.values.add(key);
            response.values.add(entry.getValue());
        }

        // Log to console
        CLog.writeToConsole(Console.MessageLevel.DEBUG, Console.MessageSource.JAVASCRIPT, new JSONObject(map).toString(4));

        return response;
    }


    private Map<String, String> getDocument(String databaseId, String docId) {
        Timber.d("getDocument: %s, %s", databaseId, docId);
        try {

            Document doc = database.getDocument(docId);
            if (doc == null) {
                return new TreeMap<>();
            }

            Map<String, String> returnedMap = new TreeMap<>();
            Timber.d("getDocument: keys length:-", doc.getKeys().size());
            for ( String entry : doc.getKeys()) {
                Timber.d("getDocument: key:-", entry);
                returnedMap.put(entry,doc.getValue(entry).toString());
            }
            return returnedMap;
        } catch (Exception e) {
            Timber.e(e.toString());
            return new TreeMap<>();
        } finally {

        }
    }
}
