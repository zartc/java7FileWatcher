package com.zc.study.fileinjection;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.integration.handler.MessageProcessor;
import org.springframework.messaging.Message;

import lombok.extern.log4j.Log4j2;

/**
 * https://docs.oracle.com/javase/tutorial/essential/io/notification.html
 *
 * @author Pascal
 */
@SpringBootApplication
@Log4j2
public class FileInjectionApplication implements ApplicationRunner, MessageProcessor<Path> {
	
	@Value("${FileInjectionApplication.dropbox}")
	private String dropboxPath;
	
	
	public static void main(String[] args) throws IOException {
		SpringApplication.run(FileInjectionApplication.class, args);
	}


	@Override
	public void run(ApplicationArguments args) throws Exception {
		log.traceEntry("c'est parti", args);
		List<String> dropboxPaths = args.getOptionValues("dropbox");
		if(dropboxPaths == null) {
			dropboxPaths = Collections.singletonList(dropboxPath);
		}
		
		Path watchedDir = FileSystems.getDefault().getPath(dropboxPaths.get(0));
		new DirectoryWatcher(watchedDir, this);
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
