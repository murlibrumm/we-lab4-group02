package highscore;

import models.JeopardyGame;
import models.Player;
import play.Logger;

import javax.xml.soap.*;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class SOAPClientHighscore {

    public static SOAPMessage sendSOAPRequest(JeopardyGame game) throws Exception {
        // Create SOAP Connection
        SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection soapConnection = soapConnectionFactory.createConnection();

        // Send SOAP Message to SOAP Server
        String url = "http://playground.big.tuwien.ac.at:8080/highscoreservice/PublishHighScoreService?wsdl";
        SOAPMessage soapResponse = soapConnection.call(createSOAPRequest(game), url);

        // Log SOAP Response
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        soapResponse.writeTo(bout);
        String soapResponseString = bout.toString("UTF-8");
        Logger.info("Response SOAP Message:");
        Logger.info(soapResponseString);

        soapConnection.close();
        return soapResponse;
    }

    private static SOAPMessage createSOAPRequest(JeopardyGame game) throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapMessage = messageFactory.createMessage();
        SOAPPart soapPart = soapMessage.getSOAPPart();

        String serverURI = "http://big.tuwien.ac.at/we/highscore/data";

        // SOAP Envelope
        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration("data", serverURI);

        /*
        Example of Constructed SOAP Request Message:
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:data="http://big.tuwien.ac.at/we/highscore/data">
            <soapenv:Header/>
            <soapenv:Body>
                <data:HighScoreRequest>
                    <data:UserKey>3ke93-gue34-dkeu9</data:UserKey>
                    <data:UserData>
                        <Loser Gender="male" BirthDate="1990-01-12">
                            <FirstName>Hans</FirstName>
                            <LastName>Mustermann</LastName>
                            <Password></Password>
                            <Points>12</Points>
                        </Loser>
                        <Winner Gender="female" BirthDate="1981-01-12">
                            <FirstName>Gerda</FirstName>
                            <LastName>Haydn</LastName>
                            <Password></Password>
                            <Points>12</Points>
                        </Winner>
                    </data:UserData>
                </data:HighScoreRequest>
            </soapenv:Body>
        </soapenv:Envelope>
         */

        // SOAP Body
        SOAPBody soapBody = envelope.getBody();
        SOAPElement highScoreRequest = soapBody.addChildElement("HighScoreRequest", "data");
            SOAPElement userKey = highScoreRequest.addChildElement("UserKey", "data");
            userKey.addTextNode("3ke93-gue34-dkeu9");
            SOAPElement userData = highScoreRequest.addChildElement("UserData", "data");

        addPlayerElement(game.getLoser(),  "Loser",  userData, envelope);
        addPlayerElement(game.getWinner(), "Winner", userData, envelope);

        soapMessage.saveChanges();

        // Log SOAP Message
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        soapMessage.writeTo(bout);
        String soapMessageString = bout.toString("UTF-8");
        Logger.info("Request SOAP Message:");
        Logger.info(soapMessageString);

        return soapMessage;
    }
    
    private static void addPlayerElement (Player player, String element, SOAPElement userData, SOAPEnvelope envelope)
            throws SOAPException {
        // Prepare Data
        Calendar cal = Calendar.getInstance();
        if (player.getUser().getBirthDate() == null) {
            cal.setTime(new Date(0));
        } else {
            cal.setTime(player.getUser().getBirthDate());
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String formatted = dateFormat.format(cal.getTime());

        String firstName = player.getUser().getUserName();
        if (!player.getUser().getFirstName().isEmpty()) {
            firstName = player.getUser().getFirstName();
        }

        String lastName = player.getUser().getUserName();
        if (!player.getUser().getFirstName().isEmpty()) {
            lastName = player.getUser().getFirstName();
        }

        // Create SOAP Elements
        Name qname;
        SOAPElement loser =  userData.addChildElement(element);
        qname = envelope.createName("Gender");
        loser.addAttribute(qname, player.getUser().getGender().toString());
        qname = envelope.createName("BirthDate");
        loser.addAttribute(qname, formatted);
        SOAPElement firstNameLoser = loser.addChildElement("FirstName");
        firstNameLoser.addTextNode(firstName);
        SOAPElement lastNameLoser = loser.addChildElement("LastName");
        lastNameLoser.addTextNode(lastName);
        SOAPElement passwordLoser = loser.addChildElement("Password");
        passwordLoser.addTextNode("");
        SOAPElement pointsLoser = loser.addChildElement("Points");
        pointsLoser.addTextNode(Integer.toString(player.getProfit()));
    }

}