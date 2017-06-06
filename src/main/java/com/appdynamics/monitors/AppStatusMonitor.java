package com.appdynamics.monitors;

import com.appdynamics.extensions.conf.MonitorConfiguration;
import com.appdynamics.extensions.util.MetricWriteHelper;
import com.appdynamics.extensions.util.MetricWriteHelperFactory;
import com.appdynamics.extensions.crypto.Decryptor;

import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;

import org.appdynamics.appdrestapi.RESTAccess;
import org.appdynamics.appdrestapi.data.Application;
import org.appdynamics.appdrestapi.data.Applications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * This is a template to create the extensions that fetches the data through the http calls.
 * The entry point is the execute() method.
 *
 * The MonitorConfiguration will do a lot of boiler plate tasks such us
 *  - Reading / Watching / Loading the config
 *  - Configure the HttpClient
 *      - Authentication
 *      - SSl Settings
 *      - Timeouts
 *      - Proxy Config
 *  - Configure the Executor Service
 *  - Scheduled Mode
 *
 * Created by abey.tom on 12/15/16.
 */
public class AppStatusMonitor extends AManagedMonitor {
    public static final Logger logger = LoggerFactory.getLogger(AppStatusMonitor.class);

    private static final String METRIC_PREFIX = "Custom Metrics|AppStatus|";
    private MonitorConfiguration configuration;

    /**
     * Prints the version of the Monitor
     */
    public AppStatusMonitor() {
        String version = getClass().getPackage().getImplementationTitle();
        String msg = String.format("Using Monitor Version [%s]", version);
        logger.info(msg);
        System.out.println(msg);
    }

    /**
     * Entry point to the Class.
     * 1) If the configuration is not initialized , then we initialize it first
     * 2) Executes the data fetch task.
     *
     * @param argsMap
     * @param taskExecutionContext
     * @return
     * @throws TaskExecutionException
     */
    public TaskOutput execute(Map<String, String> argsMap, TaskExecutionContext taskExecutionContext)
            throws TaskExecutionException {
        logger.debug("The task arguments are {}", argsMap);
        if (configuration == null) {
            initialize(argsMap);
        }
        // Handsoff the task execution to the framework. This will eventually invoke
        // TaskRunnable.run for non-scheduled mode directly.
        configuration.executeTask();
        return new TaskOutput("Whatever");
    }

    /**
     * Initialize it only once. Read config.yml path from the input args and then set it on the configuration.
     * The configuration will watch for file changes and update it automatically.
     *
     * @param argsMap
     */
    private void initialize(Map<String, String> argsMap) {
        // Get the path of config.yml from the arguments.
        final String configFilePath = argsMap.get("config-file");
        // Metric writer is needed for the workbench.
        MetricWriteHelper metricWriteHelper = MetricWriteHelperFactory.create(this);
        MonitorConfiguration conf = new MonitorConfiguration(METRIC_PREFIX, new TaskRunnable(), metricWriteHelper);
        // set the path. The framework will read/watch/load the config changes.
        conf.setConfigYml(configFilePath);
        this.configuration = conf;
    }


    /**
     * This abstraction is needed for the scheduled mode.
     * If you dont want to run the extension every minute and run it for ex every 10 mins,
     * this way will avoid the data drop. The framework will send the cached data every minute to controller and will fetch
     * the data from remote every 10 mins and update the cache.
     */
    private class TaskRunnable implements Runnable {

        public void run() {
        	Map<String, ?> configYml = configuration.getConfigYml();
        	if (configYml==null) {
        		logger.error("TaskRunnable: Unable to read config.yml");
        		return;
        	}
        	List<Map> controllerinfo = (List) configYml.get("controllerinfo");
        	if (controllerinfo==null || controllerinfo.isEmpty()) {
        		logger.error("TaskRunnable: Unable to read controllerinfo in config.yml");
        		return;
        	}
        	if (controllerinfo.size()>1) {
        		logger.error("TaskRunnable: Currently only support 1 controllerinfo in config.yml");
        		return;
        	}
        	Map firstcontroller = controllerinfo.get(0);
        	String controller = (String) firstcontroller.get("controller");
            int portInt =  (int) firstcontroller.get("port");
            String port = String.valueOf(portInt);
            String user = (String) firstcontroller.get("user");
            String password = (String) firstcontroller.get("password");
            if (password==null) {
            	password = (String) firstcontroller.get("passwordEncrypted");
            	String key = (String) configYml.get("encryptionKey");
            	Decryptor decrypt = new Decryptor(key);
            	password = decrypt.decrypt(password);
            }
            String account = (String) firstcontroller.get("account");
            boolean useSSL = (boolean) firstcontroller.get("useSSL");
            logger.debug("TaskRunnable: " + controller + " " + port  + " " + user  + " " + " " + account  + " " + useSSL);
            RESTAccess access = new RESTAccess(controller, port, useSSL, user, password, account);
            // access.setDebugLevel(2);
            Applications apps = access.getApplications();
            if (apps==null) {
            	logger.error("TaskRunnable: No applications read from " + controller);
            	return;
            }
            logger.debug("TaskRunnable: " + apps.getApplications().size() + " applications");
            for (Application app: apps.getApplications()) {
            	AppStatusMonitorTask task = new AppStatusMonitorTask(app, access, configuration);
            	configuration.getExecutorService().execute(task);
            }
            
        }
    }
}
