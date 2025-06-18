package me.berezovskyi.corsherdf.corsherdfweb

import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test
import io.restassured.RestAssured.given
import io.restassured.http.ContentType // Added for RestAssured ContentType.TEXT
import org.hamcrest.CoreMatchers.`is` // For strict body matching
import javax.ws.rs.core.MediaType // Added for MediaType.TEXT_PLAIN

@QuarkusTest
class CorsherdfWebApplicationTests {

    @Test
    fun testRProxyEndpointWithHtmlTarget() {
        val remoteUrl = "http://example.com"
        given()
          .`when`().get("/r/$remoteUrl")
          .then()
             .log().ifValidationFails() // Log response body if test fails
             .statusCode(422) // Expecting Unprocessable Entity as example.com returns HTML
             .body(`is`("Server did not return any RDF, got HTML instead from $remoteUrl"))
    }

    @Test
    fun testRProxyEndpointWithBlankUri() {
        given()
            .accept(ContentType.TEXT.toString()) // Be explicit about accepted type
          .`when`().get("/r/")
          .then()
            .log().ifValidationFails() // Log response body if test fails
            .statusCode(400) // Expecting Bad Request
            .contentType(ContentType.TEXT) // Verify content type
            .body(`is`("Pass the RDF document URI after /r/"))
    }
}
