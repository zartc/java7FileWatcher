package com.zc.study.fileinjection;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.zc.study.fileinjection.springintegration.Message;
import com.zc.study.fileinjection.springintegration.MessageProcessor;


/**
 * https://docs.oracle.com/javase/tutorial/essential/io/notification.html
 *
 * @author Pascal
 */
@SpringBootApplication
public class FileInjectionApplication implements ApplicationRunner, MessageProcessor<Path> {

	private static final Logger log = LogManager.getLogger();

	@Value("${FileInjectionApplication.dropbox}")
	private String dropboxPath;


	public static void main(String[] args) throws IOException {
		SpringApplication.run(FileInjectionApplication.class, args);
	}


	@Override
	public void run(ApplicationArguments args) throws Exception {
		List<String> watchdirs = args.getOptionValues("watchdir");
		if (watchdirs == null) {
			watchdirs = Collections.singletonList(dropboxPath);
		}

		FileSystem fs = FileSystems.getDefault();
		DirectoryWatcher directoryWatcher = new DirectoryWatcher();

		for (String string : watchdirs) {
			Path watchedDir = fs.getPath(string);
			directoryWatcher.watch(watchedDir, this);
		}

		directoryWatcher.start();
	}


	@Override
	public Path processMessage(Message<?> message) {
		log.traceEntry("processing file {}", message);

		Object eventKind = message.getHeaders().get("event.kind");
		Path path = (Path)message.getPayload();

		log.info("Processing a {} event for {}", eventKind, path);
		return log.traceExit("done processing file {}", path);
	}
}
