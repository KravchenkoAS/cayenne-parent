/*****************************************************************
 *   Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 ****************************************************************/

package org.apache.cayenne.access;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cayenne.ObjectId;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.di.Inject;
import org.apache.cayenne.query.SQLTemplate;
import org.apache.cayenne.test.jdbc.DBHelper;
import org.apache.cayenne.test.jdbc.TableHelper;
import org.apache.cayenne.testdo.testmap.Artist;
import org.apache.cayenne.testdo.testmap.Painting;
import org.apache.cayenne.unit.AccessStackAdapter;
import org.apache.cayenne.unit.di.DataChannelInterceptor;
import org.apache.cayenne.unit.di.UnitTestClosure;
import org.apache.cayenne.unit.di.server.ServerCase;
import org.apache.cayenne.unit.di.server.UseServerRuntime;

@UseServerRuntime(ServerCase.TESTMAP_PROJECT)
public class DataContextPerformQueryAPITest extends ServerCase {

    @Inject
    protected DataContext context;

    @Inject
    protected DBHelper dbHelper;

    @Inject
    protected ServerRuntime runtime;

    @Inject
    protected AccessStackAdapter accessStackAdapter;

    @Inject
    protected DataChannelInterceptor queryInterceptor;

    protected TableHelper tArtist;
    protected TableHelper tPainting;

    @Override
    protected void setUpAfterInjection() throws Exception {
        dbHelper.deleteAll("PAINTING_INFO");
        dbHelper.deleteAll("PAINTING");
        dbHelper.deleteAll("ARTIST_EXHIBIT");
        dbHelper.deleteAll("ARTIST");
        dbHelper.deleteAll("GALLERY");
        dbHelper.deleteAll("EXHIBIT");

        tArtist = new TableHelper(dbHelper, "ARTIST");
        tArtist.setColumns("ARTIST_ID", "ARTIST_NAME");

        tPainting = new TableHelper(dbHelper, "PAINTING");
        tPainting.setColumns(
                "PAINTING_ID",
                "PAINTING_TITLE",
                "ARTIST_ID",
                "ESTIMATED_PRICE");
    }

    protected void createTwoArtists() throws Exception {
        tArtist.insert(21, "artist2");
        tArtist.insert(201, "artist3");
    }

    protected void createTwoArtistsAndTwoPaintingsDataSet() throws Exception {
        tArtist.insert(11, "artist2");
        tArtist.insert(101, "artist3");
        tPainting.insert(6, "p_artist3", 101, 1000);
        tPainting.insert(7, "p_artist2", 11, 2000);
    }

    public void testObjectQueryStringBoolean() throws Exception {
        createTwoArtistsAndTwoPaintingsDataSet();

        List<?> paintings = context.performQuery("ObjectQuery", true);
        assertNotNull(paintings);
        assertEquals(2, paintings.size());
    }

    public void testObjectQueryStringMapBoolean() throws Exception {
        createTwoArtistsAndTwoPaintingsDataSet();

        Artist a = (Artist) context.localObject(new ObjectId(
                "Artist",
                Artist.ARTIST_ID_PK_COLUMN,
                11), null);
        Map<String, Artist> parameters = Collections.singletonMap("artist", a);

        List<?> paintings = ((DataContext) runtime.getContext()).performQuery(
                "ObjectQuery",
                parameters,
                true);
        assertNotNull(paintings);
        assertEquals(1, paintings.size());
    }

    public void testProcedureQueryStringMapBoolean() throws Exception {

        if (!accessStackAdapter.supportsStoredProcedures()) {
            return;
        }

        if (!accessStackAdapter.canMakeObjectsOutOfProcedures()) {
            return;
        }

        createTwoArtistsAndTwoPaintingsDataSet();

        // fetch artist
        Map<String, String> parameters = Collections.singletonMap("aName", "artist2");

        List<?> artists;

        // Sybase blows whenever a transaction wraps a SP, so turn of transactions
        boolean transactionsFlag = context
                .getParentDataDomain()
                .isUsingExternalTransactions();

        context.getParentDataDomain().setUsingExternalTransactions(true);
        try {
            artists = context.performQuery("ProcedureQuery", parameters, true);
        }
        finally {
            context.getParentDataDomain().setUsingExternalTransactions(transactionsFlag);
        }

        assertNotNull(artists);
        assertEquals(1, artists.size());

        Artist artist = (Artist) artists.get(0);
        assertEquals(33002, ((Number) artist.getObjectId().getIdSnapshot().get(
                Artist.ARTIST_ID_PK_COLUMN)).intValue());
    }

    public void testNonSelectingQueryString() throws Exception {

        int[] counts = context.performNonSelectingQuery("NonSelectingQuery");

        assertNotNull(counts);
        assertEquals(1, counts.length);
        assertEquals(1, counts[0]);

        Painting p = (Painting) context.localObject(new ObjectId(
                "Painting",
                Painting.PAINTING_ID_PK_COLUMN,
                512), null);
        assertEquals("No Painting Like This", p.getPaintingTitle());
    }

    public void testNonSelectingQueryStringMap() throws Exception {

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("id", 300);
        parameters.put("title", "Go Figure");
        parameters.put("price", new BigDecimal("22.01"));

        int[] counts = context.performNonSelectingQuery(
                "ParameterizedNonSelectingQuery",
                parameters);

        assertNotNull(counts);
        assertEquals(1, counts.length);
        assertEquals(1, counts[0]);

        Painting p = (Painting) context.localObject(new ObjectId(
                "Painting",
                Painting.PAINTING_ID_PK_COLUMN,
                300), null);
        assertEquals("Go Figure", p.getPaintingTitle());
    }

    public void testPerfomQueryNonSelecting() throws Exception {

        Artist a = context.newObject(Artist.class);
        a.setArtistName("aa");
        context.commitChanges();

        SQLTemplate q = new SQLTemplate(Artist.class, "DELETE FROM ARTIST");

        // this way of executing a query makes no sense, but it shouldn't blow either...
        List<?> result = context.performQuery(q);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    public void testObjectQueryWithLocalCache() throws Exception {
        createTwoArtists();

        List<?> artists = context.performQuery("QueryWithLocalCache", true);
        assertEquals(2, artists.size());

        queryInterceptor.runWithQueriesBlocked(new UnitTestClosure() {

            public void execute() {
                List<?> artists1 = context.performQuery("QueryWithLocalCache", false);
                assertEquals(2, artists1.size());
            }
        });
    }

    public void testObjectQueryWithSharedCache() throws Exception {
        createTwoArtists();

        List<?> artists = context.performQuery("QueryWithSharedCache", true);
        assertEquals(2, artists.size());

        queryInterceptor.runWithQueriesBlocked(new UnitTestClosure() {

            public void execute() {

                DataContext otherContext = (DataContext) runtime.getContext();
                List<?> artists1 = otherContext.performQuery(
                        "QueryWithSharedCache",
                        false);
                assertEquals(2, artists1.size());
            }
        });
    }
}
