package com.zc.study.fileinjection;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.springframework.integration.handler.MessageProcessor;
import org.springframework.messaging.support.GenericMessage;

import lombok.extern.log4j.Log4j2;


/**
 * https://docs.oracle.com/javase/tutorial/essential/io/notification.html
 * 
 * @author Pascal
 */
@Log4j2
public class DirectoryWatcher implements Runnable {

	private WatchService watcher;
	private MessageProcessor<Path> messageHandler;

	public DirectoryWatcher(Path watchedDir, MessageProcessor<Path> messageHandler) throws IOException {
		log.info("Watching directory {}", watchedDir.toAbsolutePath());

		this.messageHandler = messageHandler;
		this.watcher = FileSystems.getDefault().newWatchService();
		watchedDir.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);

		Executors.newSingleThreadExecutor().submit(this);
	}

	public void run() {
		WatchKey key;

		do {
			try {
				// wait for key to be signaled
				key = watcher.take();

				if (Thread.interrupted()) {
					return;
				}

				// then process the events
				processEvents(key.pollEvents());
			}
			catch (InterruptedException x) {
				return;
			}

			// Reset the key -- this step is critical if you want to receive further watch events.
		} while (key.reset());
	}

	private void processEvents(List<WatchEvent<?>> events) {
		for (WatchEvent<?> event : events) {

			// OVERFLOW event can occur if events are lost or discarded.
			// If the event count is greater than 1 then this is a repeated event
			if (event.kind() == OVERFLOW || event.count() > 1) {
				continue;
			}

			// create a map for the headers
			Map<String, Object> messageHeaders = new HashMap<>();
			messageHeaders.put("event.kind", event.kind());
			
			// the path is the context of the event.
			Path signaledPath = (Path)event.context();
			
			// create a message to give to the message handler
			GenericMessage<Path> message = new GenericMessage<Path>(signaledPath, messageHeaders);
			
			// delegate to the message handler
			messageHandler.processMessage(message);
		}
	}
}

/* EOF */
