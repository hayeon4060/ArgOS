package com.gte.argos.gateway.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

class FormTemplatePdfTest {

    @Test
    void 원본에서_그_양식_쪽만() throws Exception {
        byte[] pdf = FormTemplatePdf.original("F02");
        assertThat(pdf).isNotNull();
        PdfReader r = new PdfReader(pdf);
        assertThat(r.getNumberOfPages()).isEqualTo(1);
        assertThat(new PdfTextExtractor(r).getTextFromPage(1).replaceAll("\s", "")).contains("ARG-F02");
    }

    @Test
    void 원본에_없는_양식은_null() {
        assertThat(FormTemplatePdf.original("F09")).isNull();
    }
}
