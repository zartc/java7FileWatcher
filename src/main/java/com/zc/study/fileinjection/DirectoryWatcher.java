package com.zc.study.fileinjection;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zc.study.fileinjection.springintegration.GenericMessage;
import com.zc.study.fileinjection.springintegration.Message;
import com.zc.study.fileinjection.springintegration.MessageProcessor;


/**
 * https://docs.oracle.com/javase/tutorial/essential/io/notification.html
 *
 * @See http://docs.oracle.com/javase/tutorial/displayCode.html?code=http://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java
 * @author Pascal JACOB
 */
public class DirectoryWatcher implements Runnable {
	private static final Logger log = LogManager.getLogger();

	private static final ExecutorService threadExecutor = Executors.newSingleThreadExecutor();

	private WatchService watchService;

	private Map<Path, Data> watchedDirs = new ConcurrentHashMap<>();
	private Future<?> taskHandle;

	private static class Data {
		WatchKey watchKey;
		MessageProcessor<Path> messageProcessor;

		public Data(WatchKey watchKey, MessageProcessor<Path> messageProcessor) {
			this.watchKey = watchKey;
			this.messageProcessor = messageProcessor;
		}
	}

	public DirectoryWatcher() throws IOException {
		watchService = FileSystems.getDefault().newWatchService();
	}

	public void start() {
		if ((taskHandle == null) || taskHandle.isDone()) {
			taskHandle = threadExecutor.submit(this);
		}
	}

	public void stop() {
		if ((taskHandle != null) && !taskHandle.isDone()) {
			taskHandle.cancel(false);
		}
	}

	public void watch(Path watchedDir, MessageProcessor<Path> messageProcessor) throws IOException {
		WatchKey watchKey = watchedDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
		watchedDirs.put(watchedDir, new Data(watchKey, messageProcessor));

		log.info("added watching directory {}", watchedDir.toAbsolutePath().normalize());
	}

	public void remove(Path watchedDir) {
		Data data = watchedDirs.get(watchedDir);
		if (data != null) {
			data.watchKey.cancel();
			log.info("removed watching directory {}", watchedDir.toAbsolutePath().normalize());
		}
	}


	@Override
	public void run() {
		WatchKey key;

		do {
			try {
				// wait for key to be signaled
				key = watchService.take();

				if (Thread.interrupted()) {
					return;
				}

				if (key.isValid()) {
					processEvents(key);
				}
			}
			catch (InterruptedException x) {
				return;
			}

			// Reset the key -- this step is critical if you want to receive further watch events.
		} while (key.reset());
	}

	private void processEvents(WatchKey key) {
		Watchable watchable = key.watchable();
		Data data = watchedDirs.get(watchable);

		for (WatchEvent<?> event : key.pollEvents()) {

			// OVERFLOW event can occur if events are lost or discarded.
			// If the event count is greater than 1 then this is a repeated event
			if ((event.kind() == OVERFLOW) || (event.count() > 1)) {
				continue;
			}

			// create a map for the headers
			Map<String, Object> messageHeaders = new HashMap<>();
			messageHeaders.put("event.kind", event.kind());
			messageHeaders.put("watchable", watchable);

			// the path is the context of the event.
			Path signaledPath = (Path)event.context();
			Path resolvedPath = ((Path)watchable).resolve(signaledPath);

			// create a message to give to the message handler
			Message<Path> message = new GenericMessage<Path>(resolvedPath, messageHeaders);

			// delegate to the message handler
			data.messageProcessor.processMessage(message);
		}
	}
}

/* EOF */
