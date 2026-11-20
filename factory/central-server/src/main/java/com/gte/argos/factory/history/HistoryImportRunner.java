package com.gte.argos.factory.history;

import com.gte.argos.factory.config.ArgosProperties;
import java.nio.file.Path;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * argos.history-import.enabled=true 이면 서버가 켜질 때 7일치를 가져온다.
 * 이미 들어 있으면 건너뛰므로 켜 둬도 매번 다시 넣지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "argos.history-import", name = "enabled", havingValue = "true")
public class HistoryImportRunner implements ApplicationRunner {

    private final HistoryImportService service;
    private final ArgosProperties props;

    public HistoryImportRunner(HistoryImportService service, ArgosProperties props) {
        this.service = service;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.importFrom(Path.of(props.historyImport().path()), props.historyImport().reset());
    }
}
