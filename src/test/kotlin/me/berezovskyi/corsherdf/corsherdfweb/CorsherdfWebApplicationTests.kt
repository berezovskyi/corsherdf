package me.berezovskyi.corsherdf.corsherdfweb

import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Test
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.containsString

@QuarkusTest
class CorsherdfWebApplicationTests {

    @Test
    fun testRProxyEndpointWithHtmlTarget() {
        given()
          .`when`().get("/r/http://example.com")
          .then()
             .statusCode(422) // Expecting Unprocessable Entity as example.com returns HTML
             .body(containsString("Server did not return any RDF"))
    }

    @Test
    fun testRProxyEndpointWithBlankUri() {
        given()
            .`when`().get("/r/")
            .then()
            .statusCode(400) // Expecting Bad Request
            .body(containsString("Pass the RDF document URI after /r/"))
    }
}
