# AppDynamics Extension to Monitor Application Status
 
## Usage
This is an addon to the AppDynamics Machine Agent. The extension uses the AppDynamics REST API to collect events for each application in the specified controller. It compares open vs closed events to determine if the application has normal, warning, or critical status; and then it writes one custom metric status per application: if normal, 0; if warning, 1; if critical, 2. By default, the extension will be invoked every 2 minutes.

You can create health rules for the status of an application, then use health status or health list widget to show application status in a dashboard. You can also use "if Any of the following conditions are met" to show the (worst) status for a group of applications.

Historical metrics for application status will be available, but average values will be invalid if the extension runs every 2 or more minutes. The maximum value for a time period is the best metric to use when showing historical status.

## Installation
Unzip this extension under machine-agent-home/monitors. Edit config.yml to specify the controller info, including the user ID for reading the application and event data; you can only specify one controller. Note that the extension can report status metrics for applications in a different controller.

## Additional Tools
createHRs.ps1 can be used on PowerShell 2.0 or higher to create a health rule per application for all applications on a controller or for a specified list of applications.
