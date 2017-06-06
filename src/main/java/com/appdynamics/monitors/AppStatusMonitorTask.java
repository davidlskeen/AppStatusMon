package com.appdynamics.monitors;

import com.appdynamics.extensions.StringUtils;
import com.appdynamics.extensions.conf.MonitorConfiguration;

import org.appdynamics.appdrestapi.RESTAccess;
import org.appdynamics.appdrestapi.data.Application;
import org.appdynamics.appdrestapi.data.Applications;
import org.appdynamics.appdrestapi.data.Event;
import org.appdynamics.appdrestapi.data.Events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;

/**
 * This task will be executed in a threadpool.
 * <p>
 * This is a simple impl where we invoke a url and get the content length.
 * Created by abey.tom on 12/15/16.
 */
public class AppStatusMonitorTask implements Runnable {
    public static final Logger logger = LoggerFactory.getLogger(AppStatusMonitorTask.class);
    
    // public static final BigDecimal appNormal = new BigDecimal(0);
    // public static final BigDecimal appWarning = new BigDecimal(1);
    // public static final BigDecimal appCritical = new BigDecimal(2);

    private Application app;
    private RESTAccess access;
    private MonitorConfiguration configuration;

    public AppStatusMonitorTask(Application app, RESTAccess access, MonitorConfiguration configuration) {
        this.app = app;
        this.access = access;
        this.configuration = configuration;
    }

    public void run() {
    	
        logger.debug("AppStatusMonitorTask: " + app.getName());
    	long now = System.currentTimeMillis();
        long minute = 1000*60;
        int openwarning = 0;
        int opencritical = 0;
        int closewarning = 0;
        int closecritical = 0;

        Events events = access.getEvents(app.getName(), "POLICY_OPEN_CRITICAL,POLICY_CONTINUES_CRITICAL,POLICY_UPGRADED,POLICY_OPEN_WARNING,POLICY_CONTINUES_WARNING,POLICY_DOWNGRADED,POLICY_CLOSE_CRITICAL,POLICY_CANCELED_CRITICAL,POLICY_CLOSE_WARNING,POLICY_CANCELED_WARNING", "ERROR,WARN,INFO", now-(minute*30), now);
        if (events==null) { 
        	logger.error("AppStatusMonitorTask: " + app.getName() + " getEvents failed"); 
        	return;
        }
        
        for (Event event: events.getEvents()) {
        	if (event.getType().matches("POLICY_OPEN_CRITICAL|POLICY_CONTINUES_CRITICAL|POLICY_UPGRADED")) opencritical++;
        	if (event.getType().matches("POLICY_OPEN_WARNING|POLICY_CONTINUES_WARNING|POLICY_DOWNGRADED")) openwarning++;
        	if (event.getType().matches("POLICY_CLOSE_CRITICAL|POLICY_DOWNGRADED|POLICY_CANCELED_CRITICAL")) closecritical++;
        	if (event.getType().matches("POLICY_CLOSE_WARNING|POLICY_UPGRADED|POLICY_CANCELED_WARNING")) closewarning++;
        }
        logger.debug("AppStatusMonitorTask: " + app.getName() + " " + opencritical + " " + openwarning + " " + closecritical + " " + closewarning);
        
        BigDecimal appStatus;
        if (opencritical>closecritical) appStatus = new BigDecimal(2);
        else if (openwarning>closewarning) appStatus = new BigDecimal(1);
        else appStatus = new BigDecimal(0);

        String metricPrefix = configuration.getMetricPrefix();
        String metricPath = StringUtils.concatMetricPath(metricPrefix, app.getName(), "Status");
        
        /**
         * metricType = AggregationType.TimeRollup.ClusterRollup
         * AggregationType = AVG | SUM | OBS
         * TimeRollup = AVG | SUM | CUR
         * ClusterRollup = IND | COL
         */
        // Set the rollup type and report the metric.
        logger.debug("AppStatusMonitorTask: " + app.getName() + " status " + appStatus);
        configuration.getMetricWriter().printMetric(metricPath, appStatus, "AVG.AVG.COL");
    }
}
