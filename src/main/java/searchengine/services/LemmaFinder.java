package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LemmaFinder {
    private final LuceneMorphology luceneMorphology;

    public LemmaFinder() throws IOException {
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    public Map<String, Integer> getAllLemmas(String text){
        String[] russianWords = getRussianWords(text);
        Map<String, Integer> result = new HashMap<>();
        for (String word :
                russianWords) {
            if(word.isBlank() || word.length() < 3){
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

            if(result.containsKey(normalWord)) {
                result.put(normalWord, result.get(normalWord) + 1);
            }else{
                result.put(normalWord, 1);
            }
        }
        return result;
    }

    private String[] getRussianWords(String text){
        return text.toLowerCase().replaceAll("<[^>]*>", "").replaceAll("[^а-яё]+", " ").trim().split("\\s");
    }

    private boolean checkParticle(List<String> wordsMorphInfo){
        for (String wordBaseForm : wordsMorphInfo) {
            String word = wordBaseForm.toUpperCase();
            if (word.contains("МЕЖД")
                    || word.contains("СОЮЗ")
                    || word.contains("ПРЕДЛ")
                    || word.contains("ЧАСТ")
                    || word.contains("МС")
                    || word.contains("МС-П")
                    || word.contains("ВВОДН")) {
                return true;
            }
        }
        return false;
    }
}
