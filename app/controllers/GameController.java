package controllers;

import java.util.*;

import highscore.SOAPClientHighscore;
import models.Category;
import models.JeopardyDAO;
import models.JeopardyGame;
import models.JeopardyUser;
import org.springframework.util.xml.SimpleNamespaceContext;
import play.Logger;
import play.cache.Cache;
import play.data.DynamicForm;
import play.data.DynamicForm.Dynamic;
import play.data.Form;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import twitter.ITwitterClient;
import twitter.TwitterClientImpl;
import twitter.TwitterStatusMessage;
import views.html.jeopardy;
import views.html.question;
import views.html.winner;

import javax.xml.soap.*;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

@Security.Authenticated(Secured.class)
public class GameController extends Controller {
	
	protected static final int CATEGORY_LIMIT = 5;
	
	@Transactional
	public static Result index() {
		return redirect(routes.GameController.playGame());
	}
	
	@play.db.jpa.Transactional(readOnly = true)
	private static JeopardyGame createNewGame(String userName) {
		return createNewGame(JeopardyDAO.INSTANCE.findByUserName(userName));
	}
	
	@play.db.jpa.Transactional(readOnly = true)
	private static JeopardyGame createNewGame(JeopardyUser user) {
		if(user == null) // name still stored in session, but database dropped
			return null;

		Logger.info("[" + user + "] Creating a new game.");
		List<Category> allCategories = JeopardyDAO.INSTANCE.findEntities(Category.class);
		
		if(allCategories.size() > CATEGORY_LIMIT) {
			// select 5 categories randomly (simple)
			Collections.shuffle(allCategories);
			allCategories = allCategories.subList(0, CATEGORY_LIMIT);
		}
		Logger.info("Start game with " + allCategories.size() + " categories.");
		JeopardyGame game = new JeopardyGame(user, allCategories);
		cacheGame(game);
		return game;
	}
	
	private static void cacheGame(JeopardyGame game) {
		Cache.set(gameId(), game, 3600);
	}
	
	private static JeopardyGame cachedGame(String userName) {
		Object game = Cache.get(gameId());
		if(game instanceof JeopardyGame)
			return (JeopardyGame) game;
		return createNewGame(userName);
	}
	
	private static String gameId() {
		return "game." + uuid();
	}

	private static String uuid() {
		String uuid = session("uuid");
		if (uuid == null) {
			uuid = UUID.randomUUID().toString();
			session("uuid", uuid);
		}
		return uuid;
	}
	
	@Transactional
	public static Result newGame() {
		Logger.info("[" + request().username() + "] Start new game.");
		JeopardyGame game = createNewGame(request().username());
		return ok(jeopardy.render(game));
	}
	
	@Transactional
	public static Result playGame() {
		Logger.info("[" + request().username() + "] Play the game.");
		JeopardyGame game = cachedGame(request().username());
		if(game == null) // e.g., username still in session, but db dropped
			return redirect(routes.Authentication.login());
		if(game.isAnswerPending()) {
			Logger.info("[" + request().username() + "] Answer pending... redirect");
			return ok(question.render(game));
		} else if(game.isGameOver()) {
			Logger.info("[" + request().username() + "] Game over... redirect");
			return ok(winner.render(game, false, ""));
		}			
		return ok(jeopardy.render(game));
	}
	
	@play.db.jpa.Transactional(readOnly = true)
	public static Result questionSelected() {
		JeopardyGame game = cachedGame(request().username());
		if(game == null || !game.isRoundStart())
			return redirect(routes.GameController.playGame());
		
		Logger.info("[" + request().username() + "] Questions selected.");		
		DynamicForm form = Form.form().bindFromRequest();
		
		String questionSelection = form.get("question_selection");
		
		if(questionSelection == null || questionSelection.equals("") || !game.isRoundStart()) {
			return badRequest(jeopardy.render(game));
		}
		
		game.chooseHumanQuestion(Long.parseLong(questionSelection));
		
		return ok(question.render(game));
	}
	
	@play.db.jpa.Transactional(readOnly = true)
	public static Result submitAnswers() {
		JeopardyGame game = cachedGame(request().username());
		if(game == null || !game.isAnswerPending())
			return redirect(routes.GameController.playGame());
		
		Logger.info("[" + request().username() + "] Answers submitted.");
		Dynamic form = Form.form().bindFromRequest().get();
		
		@SuppressWarnings("unchecked")
		Map<String,String> data = form.getData();
		List<Long> answerIds = new ArrayList<>();
		
		for(String key : data.keySet()) {
			if(key.startsWith("answers[")) {
				answerIds.add(Long.parseLong(data.get(key)));
			}
		}
		game.answerHumanQuestion(answerIds);
		if(game.isGameOver()) {
			return redirect(routes.GameController.gameOver());
		} else {
			return ok(jeopardy.render(game));
		}
	}

	@play.db.jpa.Transactional(readOnly = true)
	public static Result gameOver() {
		JeopardyGame game = cachedGame(request().username());
		// no game was played / game not over
		if (game == null || !game.isGameOver()) {
			return redirect(routes.GameController.playGame());
		}

		// game was played and is over => SOAP Request & Twitter Post
		String uuid = "";
		try {
			uuid = sendSOAPRequest();
		} catch (Exception e) {
			Logger.error("Error while sending SOAP-Request: " + e.getMessage());
		}

		Boolean twitterSuccessful = false;
		try {
			if ( ! uuid.isEmpty() ) {
				postUUIDToTwitter(uuid);
				twitterSuccessful = true;
			}
		} catch (Exception e) {
			Logger.error("Error while posting UUID to Twitter: " + e.getMessage());
		}

		Logger.info("[" + request().username() + "] Game over.");
		return ok(winner.render(game, twitterSuccessful, uuid));
	}

	private static String sendSOAPRequest () throws Exception {
		JeopardyGame game = cachedGame(request().username());

		// Send SOAP-Request and get SOAP-Message
		SOAPMessage soapMessage = SOAPClientHighscore.sendSOAPRequest(game);

		// Get HighScoreResponse via xPath
		XPath xpath = XPathFactory.newInstance().newXPath();
		SimpleNamespaceContext ns = new SimpleNamespaceContext();

		ns.bindNamespaceUri("ns2", "http://big.tuwien.ac.at/we/highscore/data");
		ns.bindNamespaceUri("S", "http://schemas.xmlsoap.org/soap/envelope/");
		xpath.setNamespaceContext(ns);

		Node n = (Node) xpath.evaluate("//ns2:HighScoreResponse", soapMessage.getSOAPBody(), XPathConstants.NODE);
		String uuid = n.getValue();

		Logger.info("SAOP-Request successful, UUID from SOAP Message: " + uuid);
		return uuid;
	}

	private static void postUUIDToTwitter (String uuid) throws Exception {
		JeopardyGame game = cachedGame(request().username());
		TwitterStatusMessage twitterStatusMessage = new TwitterStatusMessage(
				game.getHuman().getUserName(), uuid, new Date());

		ITwitterClient twitterClient = new TwitterClientImpl();
		twitterClient.publishUuid(twitterStatusMessage);
		Logger.info("UUID sent to Twitter.");
	}
}
