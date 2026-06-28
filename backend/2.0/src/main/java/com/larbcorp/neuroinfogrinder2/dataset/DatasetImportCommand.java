package com.larbcorp.neuroinfogrinder2.dataset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatasetImportCommand implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DatasetImportCommand.class);

    private final DatasetReplayService datasetReplayService;
    private final ConfigurableApplicationContext applicationContext;

    public DatasetImportCommand(
            DatasetReplayService datasetReplayService,
            ConfigurableApplicationContext applicationContext
    ) {
        this.datasetReplayService = datasetReplayService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.getNonOptionArgs().contains("import-dataset")) {
            return;
        }

        String file = singleOption(args, "file", true);
        String name = singleOption(args, "name", false);
        String source = singleOption(args, "source", false);
        String description = singleOption(args, "description", false);
        String createdBy = singleOption(args, "createdBy", false);
        Boolean importRaw = boolOption(args, "importRaw");

        DatasetReplayService.DatasetImportResult result = datasetReplayService.importJsonl(
                new DatasetReplayService.ImportDatasetRequest(
                        file,
                        name,
                        source,
                        description,
                        createdBy,
                        importRaw
                )
        );

        log.info(
                "dataset imported datasetId={} messages={} rawImported={}",
                result.datasetId(),
                result.importedMessages(),
                result.rawImportedMessages()
        );
        applicationContext.close();
    }

    private String singleOption(ApplicationArguments args, String name, boolean required) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            if (required) {
                throw new IllegalArgumentException("Use import-dataset --file path/to/messages.jsonl --name dataset-name");
            }
            return null;
        }
        return values.get(0);
    }

    private Boolean boolOption(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(values.get(0));
    }
}
