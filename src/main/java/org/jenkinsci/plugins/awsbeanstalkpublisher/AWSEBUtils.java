package org.jenkinsci.plugins.awsbeanstalkpublisher;

import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.util.LogTaskListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalk;
import com.amazonaws.services.elasticbeanstalk.model.ApplicationDescription;
import com.amazonaws.services.elasticbeanstalk.model.DescribeApplicationsResult;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsRequest;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.elasticbeanstalk.model.EnvironmentDescription;

public class AWSEBUtils {

    private final static Pattern ENV_NAME_REGEX = Pattern.compile("[a-zA-Z0-9][-a-zA-Z0-9]{2,21}[a-zA-Z0-9]");

    private static final Logger logger = Logger.getLogger(AWSEBUtils.class.getName());
    
    public static String formatPath(String mask, Object... args) {
        return strip(String.format(mask, args).replaceAll("/{2,}", ""));
    }

    public static List<String> getValue(AbstractBuild<?, ?> build, List<String> values) {
        List<String> newValues = new ArrayList<String>(values.size());
        for (String value : values) {
            if (!value.isEmpty()) {
                newValues.add(getValue(build, value));
            }
        }
        return newValues;
    }

    public static String getValue(AbstractBuild<?, ?> build, String value) {
        return strip(replaceMacros(build, value));
    }

    public static String strip(String str) {
        return StringUtils.strip(str, "/ ");
    }
    
    public static String getEnvironmentsListAsString(AWSEBCredentials credentials, Regions region, String appName) {
        List<EnvironmentDescription> environments = getEnvironments(credentials.getAwsCredentials(), region, appName);
        StringBuilder sb = new StringBuilder();
        for (EnvironmentDescription env : environments) {
            sb.append(env.getEnvironmentName());
            sb.append("\n");
        }
        return sb.toString();
    }
    
    public static String getApplicationListAsString(AWSEBCredentials credentials, Regions region) {
        List<ApplicationDescription> apps = getApplications(credentials.getAwsCredentials(), region);
        
        
        StringBuilder sb = new StringBuilder();
        for (ApplicationDescription app : apps) {
            sb.append(app.getApplicationName());
            sb.append("\n");
        }
        return sb.toString();
    }

    public static List<String> getBadEnvironmentNames(String environments) {
        List<String> badEnv = new ArrayList<String>();
        if (environments != null && !environments.isEmpty()) {

            for (String env : environments.split("\n")) {
                if (!isValidEnvironmentName(env)) {
                    badEnv.add(env);
                }
            }
        }
        return badEnv;
    }

    public static boolean isValidEnvironmentName(String name) {
        return ENV_NAME_REGEX.matcher(name).matches();
    }

    public static List<ApplicationDescription> getApplications(AWSCredentialsProvider credentials, Regions region) {
        AWSElasticBeanstalk awseb = AWSEBDeployer.getElasticBeanstalk(credentials, Region.getRegion(region));
        DescribeApplicationsResult result = awseb.describeApplications();
        return result.getApplications();
    }

    public static List<EnvironmentDescription> getEnvironments(AWSCredentialsProvider credentials, Regions region, String appName) {
        AWSElasticBeanstalk awseb = AWSEBDeployer.getElasticBeanstalk(credentials, Region.getRegion(region));

        DescribeEnvironmentsRequest request = new DescribeEnvironmentsRequest().withApplicationName(appName);

        DescribeEnvironmentsResult result = awseb.describeEnvironments(request);
        return result.getEnvironments();
    }

    public static String replaceMacros(AbstractBuild<?, ?> build, String inputString) {
        String returnString = inputString;
        if (build != null && inputString != null) {
            try {
                Map<String, String> messageEnvVars = new HashMap<String, String>();

                messageEnvVars.putAll(build.getCharacteristicEnvVars());
                messageEnvVars.putAll(build.getBuildVariables());
                messageEnvVars.putAll(build.getEnvironment(new LogTaskListener(logger, Level.INFO)));

                returnString = Util.replaceMacro(inputString, messageEnvVars);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Couldn't replace macros in message: ", e);
            }
        }
        return returnString;

    }

}