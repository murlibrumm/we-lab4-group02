package twitter;

import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterClientImpl implements ITwitterClient {
	public void publishUuid(TwitterStatusMessage message) throws Exception {
		Configuration config = new ConfigurationBuilder()
				.setOAuthConsumerKey("GZ6tiy1XyB9W0P4xEJudQ")
				.setOAuthConsumerSecret("gaJDlW0vf7en46JwHAOkZsTHvtAiZ3QUd2mD1x26J9w")
				.setOAuthAccessToken("1366513208-MutXEbBMAVOwrbFmZtj1r4Ih2vcoHGHE2207002")
				.setOAuthAccessTokenSecret("RMPWOePlus3xtURWRVnv1TgrjTyK7Zk33evp4KKyA")
				.build();

		Twitter twitter = new TwitterFactory(config).getInstance();
		StatusUpdate status = new StatusUpdate(message.getTwitterPublicationString());
		twitter.updateStatus(status);
	}
}
