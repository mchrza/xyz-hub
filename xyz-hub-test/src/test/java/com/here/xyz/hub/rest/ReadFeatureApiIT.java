/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.xyz.hub.rest;

import static com.google.common.net.HttpHeaders.ACCEPT_ENCODING;
import static com.here.xyz.hub.rest.Api.HeaderValues.APPLICATION_GEO_JSON;
import static com.here.xyz.hub.rest.Api.HeaderValues.APPLICATION_JSON;
import static com.jayway.restassured.RestAssured.given;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_GATEWAY;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.apache.http.HttpHeaders.ETAG;
import static org.apache.http.HttpHeaders.IF_NONE_MATCH;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.vertx.core.json.JsonObject;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


public class ReadFeatureApiIT extends TestSpaceWithFeature {

  public static final String HUGE_RESPONSE_SPACE = "huge_response_test_";

  @BeforeClass
  public static void setup() {
    remove();
    createSpace();
    addFeatures();
  }

  @AfterClass
  public static void tearDownClass() {
    remove();
  }

  @Test
  public void testNotExistingForReadByBBox() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test1/bbox?north=23.13&west=113.32&south=23.14&east=113.33").
        then().
        statusCode(NOT_FOUND.code());
  }

  @Test
  public void readByBoundingBoxSmall() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/bbox?north=23.13&west=113.32&south=23.14&east=113.33").
        then().
        statusCode(OK.code()).
        body("features.size()", equalTo(1));
  }

  @Test
  public void readByBoundingBoxLarge() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/bbox?north=-90&west=-180&south=0&east=0").
        then().
        statusCode(OK.code()).
        body("features.size()", equalTo(32)).
        body("features.geometry.coordinates.findAll { it[0] >= -90 && it[0] <= 0 }.size() ", equalTo(32)).
        body("features.geometry.coordinates.findAll { it[1] >= -180 && it[1] <= 0 }.size() ", equalTo(32));
  }

  @Test
  public void readByBoundingBoxWithCache() {
    String etag =
        given().
            accept(APPLICATION_GEO_JSON).
            headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
            when().
            get("/spaces/x-psql-test/bbox?north=-90&west=-180&south=0&east=0").
            then().
            statusCode(OK.code()).
            extract().
            header(ETAG);

    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        header(IF_NONE_MATCH, etag).
        when().
        get("/spaces/x-psql-test/bbox?north=-90&west=-180&south=0&east=0").
        then().
        statusCode(NOT_MODIFIED.code());
  }

  @Test
  public void testNotExistingForCount() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test1/count").
        then().
        statusCode(NOT_FOUND.code());
  }

  @Test
  public void testFullCount() {
    given().
        accept(APPLICATION_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/count").
        then().
        statusCode(OK.code());
  }

  @Test
  public void testStatistics() {
    given().
        accept(APPLICATION_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/statistics").
        then().
        statusCode(OK.code()).
        body("count.value", greaterThanOrEqualTo(0)).
        body("count.estimated", equalTo(false));
  }

  @Test
  public void testBaseBallFeaturesCount() {
    given().
        accept(APPLICATION_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/count?tags=baseball").
        then().
        statusCode(OK.code()).
        body("count", equalTo(10));
  }

  @Test
  public void testFootBallFeaturesCount() {
    given().
        accept(APPLICATION_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/count?tags=football").
        then().
        statusCode(OK.code()).
        body("count", equalTo(41));
  }

  @Test
  public void testCommaSeparatedTags() {
    given().
        urlEncodingEnabled(false).
        accept(APPLICATION_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when()
        .get("/spaces/x-psql-test/count?tags=football,stadium").
        then().
        statusCode(OK.code()).
        body("count", equalTo(252));
  }

  @Test
  public void testCommaSeparatedTagsEncoded() {
    given().
        accept(APPLICATION_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when()
        .get("/spaces/x-psql-test/count?tags=football,stadium").
        then().
        statusCode(OK.code()).
        body("count", equalTo(0));
  }

  @Test
  public void testCombinedTags() {
    given().
        accept(APPLICATION_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/count?tags=football+stadium").
        then().
        statusCode(OK.code()).
        body("count", equalTo(41));
  }

  @Test
  public void testMultipleParameterTags() {
    given().
        accept(APPLICATION_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/count?tags=football&tags=stadium").
        then().
        statusCode(OK.code()).
        body("count", equalTo(252));
  }

  @Test
  public void testFullCountWithCache() {
    String etag =
        given().
            accept(APPLICATION_JSON).
            headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
            when().
            get("/spaces/x-psql-test/count").
            then().
            statusCode(OK.code()).
            // TODO: Fix precise counting
            //body("count", equalTo(252)).
                header("etag", notNullValue()).
            extract().
            header("etag");

    given().
        accept(APPLICATION_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        header(IF_NONE_MATCH, etag).
        when().
        get("/spaces/x-psql-test/count").
        then().
        statusCode(NOT_MODIFIED.code());
  }

  @Test
  public void testSearchSpaceRequest() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/search?limit=100&testNoValue=").
        then().
        statusCode(OK.code()).
        body("features.size()", equalTo(100));
  }

  @Test
  public void testSearchSpaceRequestWithCache() {
    String etag =
        given().
            accept(APPLICATION_GEO_JSON).
            headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
            when().
            get("/spaces/x-psql-test/search?limit=100").
            then().
            statusCode(OK.code()).
            header(ETAG, notNullValue()).
            body("features.size()", equalTo(100)).
            extract().
            header(ETAG);

    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        header(IF_NONE_MATCH, etag).
        when().
        get("/spaces/x-psql-test/search?limit=100").
        then().
        statusCode(NOT_MODIFIED.code());
  }

  @Test
  public void testNotExistingForSearchSpaceRequest() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test1/search").
        then().
        statusCode(NOT_FOUND.code());
  }

  @Test
  public void testNotExistingForIterate() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test1/iterate").
        then().
        statusCode(NOT_FOUND.code());
  }

  @Test
  public void testIterateSpaceWithoutHandleRequest() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/iterate?limit=500").
        then().
        statusCode(OK.code()).
        body("features.size()", equalTo(252)).
        body("handle", nullValue());
  }

  @Test
  public void testIterateSpaceWithInvalidHandle() {
    //This internally causes a NumberFormatException and must result in a Bad Gateway.
    given()
        .accept(APPLICATION_GEO_JSON)
        .headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN))
        .when()
        .get("/spaces/x-psql-test/iterate?limit=500&handle=dguh45gh54g98h2gfherigfhdsfifg")
        .then()
        .statusCode(BAD_GATEWAY.code())
        .body("type", equalTo("ErrorResponse"))
        .body("error", equalTo("BadGateway"))
        .body("errorMessage", equalTo("Connector error."));

    //This special handle will (starting with xyz-psql-connector version 1.2.33) cause an error response, which must as well result in a Bad Gateway.
    given()
        .accept(APPLICATION_GEO_JSON)
        .headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN))
        .when()
        .get("/spaces/x-psql-test/iterate?limit=500&handle=test_errorResponse_dguh45gh54g98h2gfherigfhdsfifg")
        .then()
        .statusCode(BAD_GATEWAY.code())
        .body("type", equalTo("ErrorResponse"))
        .body("error", equalTo("BadGateway"))
        .body("errorMessage", equalTo("Connector error."));
    given()
        .accept(APPLICATION_GEO_JSON)
        .headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN))
        .when()
        .get("/spaces/x-psql-test/iterate?limit=500&handle=test_exception_dguh45gh54g98h2gfherigfhdsfifg")
        .then()
        .statusCode(BAD_GATEWAY.code())
        .body("type", equalTo("ErrorResponse"))
        .body("error", equalTo("BadGateway"))
        .body("errorMessage", equalTo("Connector error."));
  }

  @Test
  public void testIterateSpaceWithHandleRequest() {
    String handle =
        given().
            accept(APPLICATION_GEO_JSON).
            headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
            when().
            get("/spaces/x-psql-test/iterate?limit=100").
            then().
            statusCode(OK.code()).
            body("handle", notNullValue()).
            body("'features'.size()", equalTo(100)).
            extract().
            path("handle");

    String handle2 =
        given().
            accept(APPLICATION_GEO_JSON).
            headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
            when().
            get("/spaces/x-psql-test/iterate?limit=100&handle=" + handle).
            then().
            statusCode(OK.code()).
            body("handle", notNullValue()).
            body("features.size()", equalTo(100)).
            extract().
            path("handle");

    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/iterate?limit=100&handle=" + handle2).
        then().
        statusCode(OK.code()).
        body("handle", nullValue()).
        body("features.size()", equalTo(52));
  }

  @Test
  public void testIterateSpaceWithCache() {
    String etag =
        given().
            accept(APPLICATION_GEO_JSON).
            headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
            when().
            get("/spaces/x-psql-test/iterate?limit=100").
            then().
            statusCode(OK.code()).
            header("etag", notNullValue()).
            extract().
            path("etag");

    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        header(IF_NONE_MATCH, etag).
        when().
        get("/spaces/x-psql-test/iterate?limit=100").
        then().
        statusCode(NOT_MODIFIED.code());
  }

  @Test
  public void testEmptyParams() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/iterate?limit=5&tags=").
        then().
        statusCode(OK.code());

    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/search?limit=1&tags=").
        then().
        statusCode(OK.code());

    given().
        accept(APPLICATION_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/count?tags=").
        then().
        statusCode(OK.code());
  }

  @Test
  public void testFeatureById() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/features/Q2838923").
        then().
        statusCode(OK.code()).
        body("id", equalTo("Q2838923")).
        body("properties.name", equalTo("Estadio Universidad San Marcos"));
  }

  @Test
  public void testFeaturesById() {
    given().
        urlEncodingEnabled(false).
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/features?id=Q2838923,Q856393").
        then().
        statusCode(OK.code()).
        body("features.id", hasItems("Q2838923", "Q856393"));

  }

  @Test
  public void testFeatureByIdWithCache() {
    String etag =
        given().
            accept(APPLICATION_GEO_JSON).
            headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
            when().
            get("/spaces/x-psql-test/features/Q2838923").
            then().
            statusCode(OK.code()).
            body("id", equalTo("Q2838923")).
            body("properties.name", equalTo("Estadio Universidad San Marcos")).
            header("etag", notNullValue()).
            extract().
            header("etag");

    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        header(IF_NONE_MATCH, etag).
        when().
        get("/spaces/x-psql-test/features/Q2838923").
        then().
        statusCode(NOT_MODIFIED.code());
  }

  @Test
  public void testNotExistingFeatureById() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/features/Q28389231").
        then().
        statusCode(NOT_FOUND.code());
  }

  @Test
  public void testReadingFeatureByTileId() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/tile/quadkey/2100300120310022.geojson").
        then().
        statusCode(OK.code()).
        body("features.size()", equalTo(1)).
        body("features[0].id", equalTo("Q2838923")).
        body("features[0].properties.name", equalTo("Estadio Universidad San Marcos"));
  }

  @Test
  public void testReadingFeatureByInvalidTileId() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/tile/quadkey/2100300170310022.geojson").
        then().
        statusCode(BAD_REQUEST.code());
  }

  @Test
  public void testFeatureByIdWithOtherOwner() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_2)).
        when().
        get("/spaces/x-psql-test/features/Q2838923").
        then().
        statusCode(FORBIDDEN.code());
  }

  @Test
  public void readByBoundingBoxSmallWithOtherOwner() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_2)).
        when().
        get("/spaces/x-psql-test/bbox?north=23.13&west=113.32&south=23.14&east=113.33").
        then().
        statusCode(FORBIDDEN.code());
  }

  @Test
  public void testFullCountWithOtherOwner() {
    given().
        accept(APPLICATION_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_2)).
        when().
        get("/spaces/x-psql-test/count").
        then().
        statusCode(FORBIDDEN.code());
  }

  @Test
  public void testSearchSpaceRequestWithOtherOwner() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_2)).
        when().
        get("/spaces/x-psql-test/search?limit=100").
        then().
        statusCode(FORBIDDEN.code());
  }

  @Test
  public void testIterateSpaceWithoutHandleRequestWithOtherOwner() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_2)).
        when().
        get("/spaces/x-psql-test/iterate?limit=500").
        then().
        statusCode(FORBIDDEN.code());
  }

  @Test
  public void testReadingFeatureByTileIdWithOtherOwner() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_2)).
        when().
        get("/spaces/x-psql-test/tile/quadkey/2100300120310022.geojson").
        then().
        statusCode(FORBIDDEN.code());
  }

  @Test
  public void testFeatureByIdWithAllAccess() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_ALL)).
        when().
        get("/spaces/x-psql-test/features/Q2838923").
        then().
        statusCode(OK.code());
  }

  @Test
  public void readByBoundingBoxSmallWithAllAccess() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_ALL)).
        when().
        get("/spaces/x-psql-test/bbox?north=23.13&west=113.32&south=23.14&east=113.33").
        then().
        statusCode(OK.code());
  }

  @Test
  public void testFullCountWithAllAccess() {
    given().
        accept(APPLICATION_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_ALL)).
        when().
        get("/spaces/x-psql-test/count").
        then().
        statusCode(OK.code());
  }

  @Test
  public void testSearchSpaceRequestWithAllAccess() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_ALL)).
        when().
        get("/spaces/x-psql-test/search?limit=100").
        then().
        statusCode(OK.code());
  }

  @Test
  public void testInvalidLimits() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_ALL)).
        when().
        get("/spaces/x-psql-test/search?limit=100001").
        then().
        statusCode(BAD_REQUEST.code());

    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_ALL)).
        when().
        get("/spaces/x-psql-test/search?limit=-1").
        then().
        statusCode(BAD_REQUEST.code());
  }

  @Test
  public void searchFeaturesWithEmptyTagsQueryParameter() {
    given().
        headers(getAuthHeaders(AuthProfile.ACCESS_OWNER_1_ADMIN)).
        when().
        get("/spaces/x-psql-test/search?tags=").
        then().
        statusCode(OK.code());
  }

  @Test
  public void testIterateSpaceWithoutHandleRequestWithAllAccess() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_ALL)).
        when().
        get("/spaces/x-psql-test/iterate?limit=500").
        then().
        statusCode(OK.code());
  }

  @Test
  public void testReadingFeatureByTileIdWithAllAccess() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_ALL)).
        when().
        get("/spaces/x-psql-test/tile/quadkey/2100300120310022.geojson").
        then().
        statusCode(OK.code());
  }

  @Test
  public void testFeatureByIdNotFound() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_ALL)).
        when().
        get("/spaces/x-psql-test/features/zzzzzzz").
        then().
        statusCode(NOT_FOUND.code()).
        body("errorMessage", equalTo("The requested resource does not exist."));
  }

  @Test
  public void testFeaturesByIdNotFound() {
    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_ALL)).
        when().
        get("/spaces/x-psql-test/features?id=zzzzzzz").
        then().
        statusCode(OK.code()).
        body("features", is(empty()));
  }

  @Test
  public void testLargeResponseSizePositive() {
    // Create a space which returns around 9 MB of data
    createSpaceWithSize(9);

    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_ALL)).
        header(ACCEPT_ENCODING, "").
        when().
        get("/spaces/" + cleanUpId + "/tile/quadkey/0").
        then().
        statusCode(OK.code());
  }

  @Test
  public void testLargeResponseSizeNegative() {
    createSpaceWithSize(11);

    given().
        accept(APPLICATION_GEO_JSON).
        headers(getAuthHeaders(AuthProfile.ACCESS_ALL)).
        header(ACCEPT_ENCODING, "").
        when().
        get("/spaces/" + cleanUpId + "/tile/quadkey/0").
        then().
        statusCode(OK.code());
  }

  private void createSpaceWithSize(int s) {
    // Create a space which returns around 9 MB of data
    cleanUpId = HUGE_RESPONSE_SPACE + s;
    JsonObject space = new JsonObject()
        .put("id", cleanUpId)
        .put("storage", new JsonObject().put("id", "test"))
        .put("title", "Cache Test")
        .put("cacheTTL", 3000);

    given()
        .headers(getAuthHeaders(AuthProfile.ACCESS_ALL))
        .contentType(APPLICATION_JSON)
        .body(space.encode())
        .when()
        .post("/spaces")
        .then()
        .statusCode(200);
  }
}
