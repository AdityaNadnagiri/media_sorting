package com.media.sort.runner;

import com.media.sort.cli.CLICommandHandler;
import com.media.sort.MediaSortingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Command-line runner for executing Spring Batch jobs.
 * 
 * Usage:
 * - Organize media: --job=organize [--sourceFolder=/path/to/media]
 * - Cleanup empty folders: --job=cleanup [--targetFolder=/path/to/folder]
 * - Compare folders: --job=compare --folder1Path=/path1 --folder2Path=/path2
 * 
 * Note: sourceFolder and targetFolder will default to
 * app.media-sorting.source-folder
 * from application.properties if not provided via command line.
 * 
 * If no job is specified, no job will run (application will exit).
 */
@Component
public class BatchCommandLineRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(BatchCommandLineRunner.class);

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("mediaOrganizationJob")
    private Job mediaOrganizationJob;

    @Autowired
    @Qualifier("emptyFolderCleanupJob")
    private Job emptyFolderCleanupJob;

    @Autowired
    @Qualifier("folderComparisonJob")
    private Job folderComparisonJob;

    @Autowired(required = false)
    private CLICommandHandler cliCommandHandler;

    @Autowired
    private MediaSortingProperties properties;

    @Override
    public void run(String... args) throws Exception {
        // Check for CLI commands first (--undo, --list-sessions)
        if (hasArg(args, "--undo") && cliCommandHandler != null) {
            String sessionId = getArgValue(args, "--undo");
            String baseDir = getArgValue(args, "--sourceFolder");
            cliCommandHandler.handleUndoCommand(sessionId, baseDir);
            return;
        }

        if (hasArg(args, "--list-sessions") && cliCommandHandler != null) {
            String baseDir = getArgValue(args, "--sourceFolder");
            cliCommandHandler.handleListSessionsCommand(baseDir);
            return;
        }

        String jobName = getArgValue(args, "--job");

        if (jobName == null || jobName.isEmpty()) {
            logger.info("No job specified. Use --job=organize, --job=cleanup, or --job=compare");
            logger.info("Or use: --undo=sessionId, --list-sessions");
            logger.info("Application will exit.");
            return;
        }

        logger.info("Starting batch job: {}", jobName);

        switch (jobName.toLowerCase()) {
            case "organize" -> runOrganizeJob(args);
            case "cleanup" -> runCleanupJob(args);
            case "compare" -> runCompareJob(args);
            default -> logger.error("Unknown job: {}. Valid jobs are: organize, cleanup, compare", jobName);
        }
    }

    private void runOrganizeJob(String[] args) throws Exception {
        // Try command line arg first, then fall back to application.properties
        String sourceFolder = getArgValue(args, "--sourceFolder");

        if (sourceFolder == null || sourceFolder.isEmpty()) {
            sourceFolder = properties.getSourceFolder();
            logger.info("Using source folder from application.properties: {}", sourceFolder);
        } else {
            logger.info("Using source folder from command line: {}", sourceFolder);
        }

        if (sourceFolder == null || sourceFolder.isEmpty()) {
            logger.error(
                    "Missing source folder. Provide --sourceFolder or configure app.media-sorting.source-folder in application.properties");
            return;
        }

        // Step 1: Organize media files
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("sourceFolder", sourceFolder)
                .addDate("timestamp", new Date())
                .toJobParameters();

        logger.info("Launching media organization job with sourceFolder: {}", sourceFolder);
        jobLauncher.run(mediaOrganizationJob, jobParameters);
        logger.info("Media organization job completed");

        // Step 2: Automatically run empty folder cleanup (until no more folders found)
        logger.info("Running empty folder cleanup on: {}", sourceFolder);

        int totalPasses = 0;
        int maxPasses = 50; // Safety limit to prevent infinite loops
        int foldersMovedInLastPass = 1; // Start with 1 to enter the loop

        while (foldersMovedInLastPass > 0 && totalPasses < maxPasses) {
            totalPasses++;
            logger.info("Empty folder cleanup pass #{}", totalPasses);

            JobParameters cleanupParameters = new JobParametersBuilder()
                    .addString("targetFolder", sourceFolder)
                    .addDate("timestamp", new Date())
                    .addLong("passNumber", (long) totalPasses) // Make each pass unique
                    .toJobParameters();

            var execution = jobLauncher.run(emptyFolderCleanupJob, cleanupParameters);

            // Check how many folders were processed (read count from the step execution)
            var stepExecution = execution.getStepExecutions().iterator().next();
            foldersMovedInLastPass = (int) stepExecution.getReadCount();

            logger.info("Pass #{} moved {} empty folders", totalPasses, foldersMovedInLastPass);

            if (foldersMovedInLastPass == 0) {
                logger.info("No more empty folders found, cleanup complete");
                break;
            }
        }

        if (totalPasses >= maxPasses) {
            logger.warn("Reached maximum cleanup passes ({}), stopping", maxPasses);
        }

        logger.info("Empty folder cleanup completed after {} passes", totalPasses);
    }

    private void runCleanupJob(String[] args) throws Exception {
        // Try command line arg first, then fall back to application.properties
        String targetFolder = getArgValue(args, "--targetFolder");

        if (targetFolder == null || targetFolder.isEmpty()) {
            targetFolder = properties.getSourceFolder();
            logger.info("Using target folder from application.properties: {}", targetFolder);
        } else {
            logger.info("Using target folder from command line: {}", targetFolder);
        }

        if (targetFolder == null || targetFolder.isEmpty()) {
            logger.error(
                    "Missing target folder. Provide --targetFolder or configure app.media-sorting.source-folder in application.properties");
            return;
        }

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("targetFolder", targetFolder)
                .addDate("timestamp", new Date())
                .toJobParameters();

        logger.info("Launching empty folder cleanup job with targetFolder: {}", targetFolder);
        jobLauncher.run(emptyFolderCleanupJob, jobParameters);
        logger.info("Empty folder cleanup job completed");
    }

    private void runCompareJob(String[] args) throws Exception {
        String folder1Path = getArgValue(args, "--folder1Path");
        String folder2Path = getArgValue(args, "--folder2Path");

        if (folder1Path == null || folder2Path == null) {
            logger.error("Missing required parameters: --folder1Path and --folder2Path");
            return;
        }

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("folder1Path", folder1Path)
                .addString("folder2Path", folder2Path)
                .addDate("timestamp", new Date())
                .toJobParameters();

        logger.info("Launching folder comparison job with folder1: {}, folder2: {}", folder1Path, folder2Path);
        jobLauncher.run(folderComparisonJob, jobParameters);
        logger.info("Folder comparison job completed");
    }

    /**
     * Extract argument value from command line args
     */
    private String getArgValue(String[] args, String argName) {
        for (String arg : args) {
            if (arg.startsWith(argName + "=")) {
                return arg.substring(argName.length() + 1);
            }
        }
        return null;
    }

    /**
     * Check if argument exists
     */
    private boolean hasArg(String[] args, String argName) {
        for (String arg : args) {
            if (arg.startsWith(argName)) {
                return true;
            }
        }
        return false;
    }
}
