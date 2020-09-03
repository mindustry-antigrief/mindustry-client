package mindustry.client.utils;

// Taken from https://rosettacode.org/wiki/Levenshtein_distance

public class Levenshtein{

    public static int distance(String a, String b){
        a = a.toLowerCase();
        b = b.toLowerCase();
        // i == 0
        int[] costs = new int[b.length() + 1];
        for(int j = 0; j < costs.length; j++)
            costs[j] = j;
        for(int i = 1; i <= a.length(); i++){
            // j == 0; nw = lev(i - 1, j)
            costs[0] = i;
            int nw = i - 1;
            for(int j = 1; j <= b.length(); j++){
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    public static float distanceCompletion(String word, String word2){
        if(word2.toLowerCase().contains(word)){
            if(word2.toLowerCase().equals(word.toLowerCase())){
                return 0F;
            }
            if(word2.toLowerCase().startsWith(word)){
                // Discount for if the word starts with the input
                return 0.25F * Levenshtein.distance(word.toLowerCase(), word2.toLowerCase());
            }else{
                // Discount for if the word contains the input
                return 0.5F * Levenshtein.distance(word.toLowerCase(), word2.toLowerCase());
            }
        }
        return Levenshtein.distance(word, word2);
    }
}
