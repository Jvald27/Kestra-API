package io.kestra.webserver.controllers.api;

import static io.micronaut.http.HttpRequest.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowForExecution;
import io.kestra.core.models.tasks.TaskForExecution;
import io.kestra.core.models.triggers.AbstractTriggerForExecution;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Slf4j
@KestraTest
class ExecutionControllerTest {

    @Inject
    ExecutionController executionController;

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    public static final String TESTS_FLOW_NS = "io.kestra.tests";
    public static final String TESTS_WEBHOOK_KEY = "a-secret-key";

    @SneakyThrows
    @BeforeEach
    protected void setup() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();

        TestsUtils.loads(repositoryLoader);
    }

    @Test
    void getNotFound() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/executions/exec_id_not_found"))
        );

        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    private MultipartBody createInputsFlowBody() {
        // Trigger execution
        File applicationFile = new File(Objects.requireNonNull(
            ExecutionControllerTest.class.getClassLoader().getResource("application-test.yml")
        ).getPath());

        File logbackFile = new File(Objects.requireNonNull(
            ExecutionControllerTest.class.getClassLoader().getResource("logback.xml")
        ).getPath());

        return MultipartBody.builder()
            .addPart("string", "myString")
            .addPart("enum", "ENUM_VALUE")
            .addPart("int", "42")
            .addPart("float", "42.42")
            .addPart("instant", "2019-10-06T18:27:49Z")
            .addPart("files", "file", MediaType.TEXT_PLAIN_TYPE, applicationFile)
            .addPart("files", "optionalFile", MediaType.TEXT_XML_TYPE, logbackFile)
            .addPart("secret", "secret")
            .addPart("array", "[1, 2, 3]")
            .addPart("json", "{}")
            .addPart("yaml", "{}")
            .build();
    }

    @Test
    void webhookFlowNotFound() {
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest
                    .POST(
                        "/api/v1/executions/webhook/not-found/webhook/not-found?name=john&age=12&age=13",
                        ImmutableMap.of("a", 1, "b", true)
                    ),
                Execution.class
            )
        );
        assertThat(exception.getStatus(), is(HttpStatus.NOT_FOUND));
        assertThat(exception.getMessage(), containsString("Not Found: Flow not found"));

        exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest
                    .PUT(
                        "/api/v1/executions/webhook/not-found/webhook/not-found?name=john&age=12&age=13",
                        Collections.singletonList(ImmutableMap.of("a", 1, "b", true))
                    ),
                Execution.class
            )
        );
        assertThat(exception.getStatus(), is(HttpStatus.NOT_FOUND));
        assertThat(exception.getMessage(), containsString("Not Found: Flow not found"));

        exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest
                    .POST(
                        "/api/v1/executions/webhook/not-found/webhook/not-found?name=john&age=12&age=13",
                        "bla"
                    ),
                Execution.class
            )
        );
        assertThat(exception.getStatus(), is(HttpStatus.NOT_FOUND));
        assertThat(exception.getMessage(), containsString("Not Found: Flow not found"));

        exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                GET("/api/v1/executions/webhook/not-found/webhook/not-found?name=john&age=12&age=13"),
                Execution.class
            )
        );
        assertThat(exception.getStatus(), is(HttpStatus.NOT_FOUND));
        assertThat(exception.getMessage(), containsString("Not Found: Flow not found"));

        exception = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest
                    .POST(
                        "/api/v1/executions/webhook/not-found/webhook/not-found?name=john&age=12&age=13",
                        "{\\\"a\\\":\\\"\\\",\\\"b\\\":{\\\"c\\\":{\\\"d\\\":{\\\"e\\\":\\\"\\\",\\\"f\\\":\\\"1\\\"}}}}"
                    ),
                Execution.class
            )
        );
        assertThat(exception.getStatus(), is(HttpStatus.NOT_FOUND));
        assertThat(exception.getMessage(), containsString("Not Found: Flow not found"));
    }

    @Test
    void webhookDynamicKey() {
        Execution execution = client.toBlocking().retrieve(
            GET(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook-dynamic-key/webhook-dynamic-key"
                ),
            Execution.class
        );

        assertThat(execution, notNullValue());
        assertThat(execution.getId(), notNullValue());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SECRET_WEBHOOK_KEY", matches = ".*")
    void webhookDynamicKeyFromASecret() {
        Execution execution = client.toBlocking().retrieve(
            GET(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook-secret-key/secretKey"
                ),
            Execution.class
        );

        assertThat(execution, notNullValue());
        assertThat(execution.getId(), notNullValue());
    }

    @Test
    void webhookWithCondition() {
        record Hello(String hello) {}

        Execution execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook-with-condition/webhookKey",
                    new Hello("world")
                ),
            Execution.class
        );

        assertThat(execution, notNullValue());
        assertThat(execution.getId(), notNullValue());

        HttpResponse<Execution> response = client.toBlocking().exchange(
            HttpRequest
                .POST(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook-with-condition/webhookKey",
                    new Hello("webhook")
                ),
            Execution.class
        );
        assertThat(response.getStatus(), is(HttpStatus.NO_CONTENT));
        assertThat(response.body(), nullValue());
    }

    @Test
    void resolveAbsoluteDateTime() {
        final ZonedDateTime absoluteTimestamp = ZonedDateTime.of(2023, 2, 3, 4, 6,10, 0, ZoneId.systemDefault());
        final Duration offset = Duration.ofSeconds(5L);
        final ZonedDateTime baseTimestamp = ZonedDateTime.of(2024, 2, 3, 5, 6,10, 0, ZoneId.systemDefault());

        assertThat(executionController.resolveAbsoluteDateTime(absoluteTimestamp, null, null), is(absoluteTimestamp));
        assertThat(executionController.resolveAbsoluteDateTime(null, offset, baseTimestamp), is(baseTimestamp.minus(offset)));
        assertThrows(IllegalArgumentException.class, () -> executionController.resolveAbsoluteDateTime(absoluteTimestamp, offset, baseTimestamp));
    }


    @Test
    void nullLabels() {
        MultipartBody requestBody = createInputsFlowBody();

        // null keys are forbidden
        MutableHttpRequest<MultipartBody> requestNullKey = HttpRequest
            .POST("/api/v1/executions/" + TESTS_FLOW_NS + "/inputs?labels=:value", requestBody)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(requestNullKey, Execution.class));

        // null values are forbidden
        MutableHttpRequest<MultipartBody> requestNullValue = HttpRequest
            .POST("/api/v1/executions/" + TESTS_FLOW_NS + "/inputs?labels=key:", requestBody)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(requestNullValue, Execution.class));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void getFlowForExecution() {
        FlowForExecution result = client.toBlocking().retrieve(
            GET("/api/v1/executions/flows/io.kestra.tests/full"),
            FlowForExecution.class
        );

        assertThat(result, notNullValue());
        assertThat(result.getTasks(), hasSize(5));
        assertThat((result.getTasks().getFirst() instanceof TaskForExecution), is(true));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void getFlowForExecutionById() {
        Execution execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + TESTS_WEBHOOK_KEY + "?name=john&age=12&age=13",
                    ImmutableMap.of("a", 1, "b", true)
                ),
            Execution.class
        );

        FlowForExecution result = client.toBlocking().retrieve(
            GET("/api/v1/executions/" + execution.getId() + "/flow"),
            FlowForExecution.class
        );

        assertThat(result.getId(), is(execution.getFlowId()));
        assertThat(result.getTriggers(), hasSize(1));
        assertThat((result.getTriggers().getFirst() instanceof AbstractTriggerForExecution), is(true));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getDistinctNamespaceExecutables() {
        List<String> result = client.toBlocking().retrieve(
            GET("/api/v1/executions/namespaces"),
            Argument.of(List.class, String.class)
        );

        assertThat(result.size(), greaterThanOrEqualTo(5));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getFlowFromNamespace() {
        List<FlowForExecution> result = client.toBlocking().retrieve(
            GET("/api/v1/executions/namespaces/io.kestra.tests/flows"),
            Argument.of(List.class, FlowForExecution.class)
        );

        assertThat(result.size(), greaterThan(100));
    }

    @Test
    void badDate() {
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().retrieve(GET(
                "/api/v1/executions/search?filters[startDate][$eq]=2024-06-03T00:00:00.000%2B02:00&filters[endDate][$eq]=2023-06-05T00:00:00.000%2B02:00"), PagedResults.class));
        assertThat(exception.getStatus().getCode(), is(422));
        assertThat(exception.getMessage(),is("Illegal argument: Start date must be before End Date"));

        HttpClientResponseException exception_oldParameters = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().retrieve(GET(
                "/api/v1/executions/search?startDate=2024-06-03T00:00:00.000%2B02:00&endDate=2023-06-05T00:00:00.000%2B02:00"), PagedResults.class));
        assertThat(exception_oldParameters.getStatus().getCode(), is(422));
        assertThat(exception_oldParameters.getMessage(),is("Illegal argument: Start date must be before End Date"));
    }

    @Test
    void commaInSingleLabelsValue() {
        String encodedCommaWithinLabel = URLEncoder.encode("project:foo,bar", StandardCharsets.UTF_8);

        MutableHttpRequest<Object> deleteRequest = HttpRequest
            .DELETE("/api/v1/executions/by-query?labels=" + encodedCommaWithinLabel);
        assertDoesNotThrow(() -> client.toBlocking().retrieve(deleteRequest, PagedResults.class));

        MutableHttpRequest<List<Object>> restartRequest = HttpRequest
            .POST("/api/v1/executions/restart/by-query?labels=" + encodedCommaWithinLabel, List.of());
        assertDoesNotThrow(() -> client.toBlocking().retrieve(restartRequest, BulkResponse.class));

        MutableHttpRequest<List<Object>> resumeRequest = HttpRequest
            .POST("/api/v1/executions/resume/by-query?labels=" + encodedCommaWithinLabel, List.of());
        assertDoesNotThrow(() -> client.toBlocking().retrieve(resumeRequest, BulkResponse.class));

        MutableHttpRequest<List<Object>> replayRequest = HttpRequest
            .POST("/api/v1/executions/replay/by-query?labels=" + encodedCommaWithinLabel, List.of());
        assertDoesNotThrow(() -> client.toBlocking().retrieve(replayRequest, BulkResponse.class));

        MutableHttpRequest<List<Object>> labelsRequest = HttpRequest
            .POST("/api/v1/executions/labels/by-query?labels=" + encodedCommaWithinLabel, List.of());
        assertDoesNotThrow(() -> client.toBlocking().retrieve(labelsRequest, BulkResponse.class));

        MutableHttpRequest<List<Object>> killRequest = HttpRequest
            .DELETE("/api/v1/executions/kill/by-query?labels=" + encodedCommaWithinLabel, List.of());
        assertDoesNotThrow(() -> client.toBlocking().retrieve(killRequest, BulkResponse.class));

        MutableHttpRequest<MultipartBody> triggerRequest = HttpRequest
            .POST("/api/v1/executions/trigger/" + TESTS_FLOW_NS + "/inputs?labels=" + encodedCommaWithinLabel, createInputsFlowBody())
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
        assertThat(client.toBlocking().retrieve(triggerRequest, Execution.class).getLabels(), hasItem(new Label("project", "foo,bar")));

        MutableHttpRequest<MultipartBody> createRequest = HttpRequest
            .POST("/api/v1/executions/" + TESTS_FLOW_NS + "/inputs?labels=" + encodedCommaWithinLabel, createInputsFlowBody())
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
        assertThat(client.toBlocking().retrieve(createRequest, Execution.class).getLabels(), hasItem(new Label("project", "foo,bar")));

        MutableHttpRequest<Object> searchRequest = HttpRequest
            .GET("/api/v1/executions/search?filters[labels][$eq][project]=foo,bar");
        assertThat(client.toBlocking().retrieve(searchRequest, PagedResults.class).getTotal(), is(2L));

        MutableHttpRequest<Object> searchRequest_oldParameters = HttpRequest
            .GET("/api/v1/executions/search?labels=project:foo,bar");
        assertThat(client.toBlocking().retrieve(searchRequest_oldParameters, PagedResults.class).getTotal(), is(2L));
    }

    @Test
    void commaInOneOfMultiLabels() {
        String encodedCommaWithinLabel = URLEncoder.encode("project:foo,bar", StandardCharsets.UTF_8);
        String encodedRegularLabel = URLEncoder.encode("status:test", StandardCharsets.UTF_8);

        MutableHttpRequest<MultipartBody> createRequest = HttpRequest
            .POST("/api/v1/executions/" + TESTS_FLOW_NS + "/inputs?labels=" + encodedCommaWithinLabel + "&labels=" + encodedRegularLabel, createInputsFlowBody())
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
        assertThat(client.toBlocking().retrieve(createRequest, Execution.class).getLabels(), hasItems(
            new Label("project", "foo,bar"),
            new Label("status", "test")
        ));

        MutableHttpRequest<Object> searchRequest = HttpRequest
            .GET("/api/v1/executions/search?filters[labels][$eq][project]=foo,bar" + "&filters[labels][$eq][status]=test");
        assertThat(client.toBlocking().retrieve(searchRequest, PagedResults.class).getTotal(), is(1L));

        MutableHttpRequest<Object> searchRequest_oldParameters = HttpRequest
            .GET("/api/v1/executions/search?labels=project:foo,bar" + "&labels=status:test");
        assertThat(client.toBlocking().retrieve(searchRequest_oldParameters, PagedResults.class).getTotal(), is(1L));
    }

    @Test
    void scheduleDate() {
        // given
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        String scheduleDate = URLEncoder.encode(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(now), StandardCharsets.UTF_8);

        // when
        MutableHttpRequest<?> createRequest = HttpRequest
            .POST("/api/v1/executions/" + TESTS_FLOW_NS + "/minimal?scheduleDate=" + scheduleDate, null)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE);
        Execution execution = client.toBlocking().retrieve(createRequest, Execution.class);

        // then
        assertThat(execution.getScheduleDate(), is(now.toInstant()));
    }

    @Test
    void shouldValidateInputsForCreateGivenSimpleInputs() {
        // given
        String namespace = "io.kestra.tests";
        String flowId = "inputs";

        MultipartBody requestBody = MultipartBody.builder()
            .addPart("string", "myString")
            .build();
        // when
        ExecutionController.ApiValidateExecutionInputsResponse response = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/" + namespace + "/" + flowId + "/validate", requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            ExecutionController.ApiValidateExecutionInputsResponse.class
        );

        // then
        Assertions.assertNotNull(response);
        Assertions.assertEquals(flowId, response.id());
        Assertions.assertEquals(namespace, response.namespace());
        Assertions.assertFalse(response.inputs().isEmpty());
        Assertions.assertTrue(response.inputs().stream().allMatch(ExecutionController.ApiValidateExecutionInputsResponse.ApiInputAndValue::enabled));
    }

    @Test
   void shouldHaveAnUrlWhenCreated() {
        // ExecutionController.ExecutionResponse cannot be deserialized because it didn't have any default constructor.
        // adding it would mean updating the Execution itself, which is too annoying, so for the test we just deserialize to a Map.
        Map<?, ?> executionResult = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/" + TESTS_FLOW_NS + "/minimal", null)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Map.class
        );

        assertThat(executionResult, notNullValue());
        assertThat(executionResult.get("url"), is("http://localhost:8081/ui/executions/io.kestra.tests/minimal/" + executionResult.get("id")));
    }

    @Test
    void shouldRefuseSystemLabelsWhenCreatingAnExecution() {
        var error = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/io.kestra.tests/minimal?labels=system.label:system", null)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        ));

        assertThat(error.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
    }
}
