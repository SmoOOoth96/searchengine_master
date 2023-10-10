package searchengine.services;

import lombok.extern.log4j.Log4j2;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
@Log4j2
public class LemmaFinder {
    private final LuceneMorphology luceneMorphology;

    private final String[] speechParts = new String[]{"МЕЖД", "СОЮЗ", "ПРЕДЛ", "ЧАСТ", "МС", "МС-П", "ВВОДН"};

    public LemmaFinder() throws IOException {
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    public Map<String, Integer> getLemmasAndFrequency(String text){
        Map<String, Integer> result = null;
        try {
            String[] russianWords = getRussianWords(text);
            result = new HashMap<>();
            for (String word :
                    russianWords) {
                if(checkLength(word)){
                    continue;
                }

                List<String> wordsMorphInfo = luceneMorphology.getMorphInfo(word);
                if(checkParticle(wordsMorphInfo)){
                    continue;
                }

                List<String> wordsNormalForm = luceneMorphology.getNormalForms(word);
                if(wordsNormalForm.isEmpty()){
                    continue;
                }

                String normalWord = wordsNormalForm.get(0);

                if (result.containsKey(normalWord)) {
                    result.put(normalWord, result.get(normalWord) + 1);
                } else {
                    result.put(normalWord, 1);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("error", e);
        }
        return result;
    }

    public List<String> getLemmas(String text){
        List<String> result = null;
        try {
            String[] russianWords = getRussianWords(text);
            result = new ArrayList<>();
            for (String word :
                    russianWords) {
                if(checkLength(word)){
                    continue;
                }

                List<String> wordsMorphInfo = luceneMorphology.getMorphInfo(word);
                if(checkParticle(wordsMorphInfo)){
                    continue;
                }

                List<String> wordsNormalForm = luceneMorphology.getNormalForms(word);
                if(wordsNormalForm.isEmpty()){
                    continue;
                }
                result.add(wordsNormalForm.get(0));
            }
            return result;
        } catch (Exception e) {
            log.error("error", e);
        }
        return result;
    }

    private String[] getRussianWords(String text){
        return text.toLowerCase()
                .replaceAll("<[^>]*>", " ")
                .replaceAll("[^а-я]+", " ")
                .trim()
                .split("\\s");
    }

    private boolean checkParticle(List<String> wordsMorphInfo){
        for (String wordBaseForm : wordsMorphInfo) {
            String word = wordBaseForm.toUpperCase();
            return containsPartsSpeech(word);
        }
        return false;
    }

    private boolean containsPartsSpeech(String word) {
        return Arrays.stream(speechParts).anyMatch(word::contains);
    }

    private boolean checkLength(String word) {
        return word.isBlank() || word.length() < 3;
    }
}
