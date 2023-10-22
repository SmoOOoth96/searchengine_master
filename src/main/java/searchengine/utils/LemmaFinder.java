package searchengine.utils;

import lombok.extern.log4j.Log4j2;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
@Log4j2
public class LemmaFinder {
    private final LuceneMorphology russianLuceneMorphology;
    private final LuceneMorphology englishLuceneMorphology;

    private final String[] russianSpeechParts = new String[]{"МЕЖД", "СОЮЗ", "ПРЕДЛ", "ЧАСТ", "МС", "МС-П", "ВВОДН"};
    private final String[] englishSpeechParts = new String[]{"INT", "CONJ", "PREP", "PART", "PN pers", "PN_ADJ", "ARTICLE"};

    public LemmaFinder() throws IOException {
        this.russianLuceneMorphology = new RussianLuceneMorphology();
        this.englishLuceneMorphology = new EnglishLuceneMorphology();
    }

    public Map<String, Integer> getLemmasAndFrequency(String text){
        Map<String, Integer> result = null;
        try {
            String[] allWords = getAllWords(text);
            result = new HashMap<>();
            for (String word :
                    allWords) {
                if(checkLength(word)){
                    continue;
                }

                List<String> wordsNormalForm = null;
                if (isRussian(word)) {
                    List<String> wordsMorphInfo = russianLuceneMorphology.getMorphInfo(word);
                    if(checkParticle(wordsMorphInfo)){
                        continue;
                    }

                    wordsNormalForm = russianLuceneMorphology.getNormalForms(word);
                    if(wordsNormalForm.isEmpty()){
                        continue;
                    }
                }else if(isEnglish(word)){
                    List<String> wordsMorphInfo = englishLuceneMorphology.getMorphInfo(word);
                    if(checkParticle(wordsMorphInfo)){
                        continue;
                    }

                    wordsNormalForm = englishLuceneMorphology.getNormalForms(word);
                    if(wordsNormalForm.isEmpty()){
                        continue;
                    }
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
            String[] allWords = getAllWords(text);
            result = new ArrayList<>();
            for (String word :
                    allWords) {
                if(checkLength(word)){
                    continue;
                }

                List<String> wordsNormalForm = null;
                if (isRussian(word)) {
                    List<String> wordsMorphInfo = russianLuceneMorphology.getMorphInfo(word);
                    if(checkParticle(wordsMorphInfo)){
                        continue;
                    }

                    wordsNormalForm = russianLuceneMorphology.getNormalForms(word);
                    if(wordsNormalForm.isEmpty()){
                        continue;
                    }
                }else if (isEnglish(word)){
                    List<String> wordsMorphInfo = englishLuceneMorphology.getMorphInfo(word);
                    if(checkParticle(wordsMorphInfo)){
                        continue;
                    }

                    wordsNormalForm = englishLuceneMorphology.getNormalForms(word);
                    if(wordsNormalForm.isEmpty()){
                        continue;
                    }
                }

                result.add(wordsNormalForm.get(0));
            }
            return result;
        } catch (Exception e) {
            log.error("error", e);
        }
        return result;
    }

    private String[] getAllWords(String text){
        return text.toLowerCase()
                .replaceAll("<[^>]*>", " ")
                .replaceAll("[^а-яa-z]+", " ")
                .trim()
                .split("\\s");
    }

    private boolean checkParticle(List<String> wordsMorphInfo){
        for (String wordBaseForm : wordsMorphInfo) {
            String word = wordBaseForm.toUpperCase();
            if(isRussian(word)) {
                return containsRussianPartsSpeech(word);
            }else if(isEnglish(word)){
                return containsEnglishPartsSpeech(word);
            }
        }
        return false;
    }

    private boolean containsRussianPartsSpeech(String word) {
        return Arrays.stream(russianSpeechParts).anyMatch(word::contains);
    }

    private boolean containsEnglishPartsSpeech(String word) {
        return Arrays.stream(englishSpeechParts).anyMatch(word::contains);
    }

    private boolean checkLength(String word) {
        return word.isBlank() || word.length() < 3;
    }

    private boolean isEnglish(String word) {
        return word.matches("[a-zA-Z]+");
    }

    private boolean isRussian(String word) {
        return word.matches("[ёа-яЁА-Я]+");
    }
}
