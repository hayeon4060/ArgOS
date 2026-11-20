package com.gte.argos.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    @Test
    void 파일_칸이_있으면_multipart_로_바꾼다() {
        Operation op = operation(new StringSchema().format("binary"));
        OpenApiConfig.fixFileUpload(op);
        assertThat(op.getRequestBody().getContent()).containsOnlyKeys("multipart/form-data");
    }

    @Test
    void 일반_JSON_본문은_그대로() {
        Operation op = operation(new StringSchema());
        OpenApiConfig.fixFileUpload(op);
        assertThat(op.getRequestBody().getContent()).containsOnlyKeys("application/json");
    }

    @Test
    void 모든_컨트롤러에_화면_이름이_있다() {
        assertThat(OpenApiConfig.TAGS).hasSize(18).containsKey("auth-controller");
    }

    private static Operation operation(Schema<?> field) {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("file", field);
        return new Operation().requestBody(new RequestBody().content(
                new Content().addMediaType("application/json", new MediaType().schema(schema))));
    }
}
