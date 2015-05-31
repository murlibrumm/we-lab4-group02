package controllers;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.sparql.vocabulary.FOAF;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

import at.ac.tuwien.big.we.dbpedia.api.DBPediaService;
import at.ac.tuwien.big.we.dbpedia.api.SelectQueryBuilder;
import at.ac.tuwien.big.we.dbpedia.vocabulary.DBPedia;
import at.ac.tuwien.big.we.dbpedia.vocabulary.DBPediaOWL;
import at.ac.tuwien.big.we.dbpedia.vocabulary.DBPProp;
import at.ac.tuwien.big.we.dbpedia.vocabulary.Skos;

import java.lang.System;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

import models.Category;
import models.Question;
import models.Answer;
import models.JeopardyDAO;

import java.util.Random;

public class DBPediaClient{

    public static final int FIN_LIMIT = 30;

    public static void createDBPediaQuestions(){
        Category category = new Category();
        category.setName("Movies", "en");
        category.setName("Filme",  "de");
        addQuestion(50, "Alfred_Hitchcock",  FIN_LIMIT, category);
        addQuestion(40, "Tim_Burton",        FIN_LIMIT, category);
        addQuestion(30, "Martin_Scorsese",   FIN_LIMIT, category);
        addQuestion(20, "Christopher_Nolan", FIN_LIMIT, category);
        addQuestion(10, "Steven_Spielberg",  FIN_LIMIT, category);
        JeopardyDAO.INSTANCE.persist(category);
    }

    private static void addQuestion(int value, String name, int limit, Category category) {
        System.out.println("=====" + name);
        Random randomGenerator = new Random();

        Question question = new Question();
        String regName = name.replace("_", " ");
        String questiontext = regName + ".";
        question.setText(questiontext, "de");
        question.setText(questiontext, "en");
        question.setValue(value);

        // Check if DBpedia is available
        if( ! DBPediaService.isAvailable() ) {
            return;
        }

        // Load all statements as we need to get the name later
        Resource resource = DBPediaService.loadStatements(DBPedia.createResource(name));

        // retrieve english and german names, might be used for question text
        String englishName = DBPediaService.getResourceName(resource, Locale.ENGLISH);
        String germanName  = DBPediaService.getResourceName(resource, Locale.GERMAN);

        SelectQueryBuilder movieQuery = DBPediaService.createQueryBuilder()
                .setLimit(limit)
                .addWhereClause(RDF.type, DBPediaOWL.Film)
                .addPredicateExistsClause(FOAF.name)
                .addWhereClause(DBPediaOWL.director, resource)
                .addFilterClause(RDFS.label, Locale.GERMAN)
                .addFilterClause(RDFS.label, Locale.ENGLISH);

        // retrieve data from dbpedia
        Model movies = DBPediaService.loadStatements(movieQuery.toQueryString());

        // alter query to get movies without director
        movieQuery.removeWhereClause(DBPediaOWL.director, resource);
        movieQuery.addMinusClause(DBPediaOWL.director, resource);

        // get english and german movie names, e.g., for right choices
        List<String> englishMovieNames = DBPediaService.getResourceNames(movies, Locale.ENGLISH);
        List<String> germanMovieNames  = DBPediaService.getResourceNames(movies, Locale.GERMAN);

        // retrieve data from dbpedia
        Model wrongMovies = DBPediaService.loadStatements(movieQuery.toQueryString());
        // get english and german movie names, e.g., for wrong choices
        List<String> englishWrongMovieNames = DBPediaService.getResourceNames(wrongMovies, Locale.ENGLISH);
        List<String> germanWrongMovieNames  = DBPediaService.getResourceNames(wrongMovies, Locale.GERMAN);

        // List<Answer> und Listen mit Movienames, die noch nicht zur List<Answer> hinzugefuegt wurden
        List<Answer> answers = new ArrayList<Answer>();
        List<Integer> usedIndexMovieNames = new ArrayList<Integer>();
        List<Integer> usedIndexWrongMovieNames = new ArrayList<Integer>();

        // zumindest eine richtige antwort
        int index = getUnusedIndex (usedIndexMovieNames, englishMovieNames.size());
        setAnswer (englishMovieNames, germanMovieNames, question, answers, index, true);

        // 1 richtige + 3 bis 5 richtig / falsche Antworten.
        int questionNumber = 3 + randomGenerator.nextInt(3);
        for (int i = 0; i < questionNumber; i++) {
            int rightAnswer = randomGenerator.nextInt(2);
            if (rightAnswer == 0) {
                index = getUnusedIndex(usedIndexWrongMovieNames, englishWrongMovieNames.size());
                setAnswer(englishWrongMovieNames, germanWrongMovieNames, question, answers, index, false);
            } else {
                index = getUnusedIndex(usedIndexMovieNames, englishMovieNames.size());
                setAnswer(englishMovieNames, germanMovieNames, question, answers, index, true);
            }
        }

        question.setAnswers(answers);
        JeopardyDAO.INSTANCE.persist(question);
        question.setCategory(category);
        category.addQuestion(question);
    }

    private static void setAnswer (List<String> englishMovieNames, List<String> germanMovieNames,
                                   Question question, List<Answer> answers, int index, boolean trueAnswer) {
        if (index == -1) {
            return;
        }
        Answer answer = new Answer();

        System.out.println(englishMovieNames.get(index) + " ----- " + trueAnswer);

        answer.setText("Who directed the movie " + englishMovieNames.get(index) + "?", "en");
        answer.setText("Wer war Regisseur bei "  + germanMovieNames.get(index)  + "?", "de");
        answer.setCorrectAnswer(trueAnswer);

        JeopardyDAO.INSTANCE.persist(answer);
        if (trueAnswer) {
            question.addRightAnswer(answer);
        } else {
            question.addWrongAnswer(answer);
        }
        answers.add(answer);
    }

    private static int getUnusedIndex (List<Integer> usedIndexes, int size) {
        if (usedIndexes.size() == size) {
            return -1;
        }
        Random randomGenerator = new Random();

        int index = -1;
        do {
            index = randomGenerator.nextInt(size);
        } while ( usedIndexes.contains(index) );
        usedIndexes.add(index);

        return index;
    }
}