package mindustry.client.utils;

public class BiasedLevenshtein {

    public static float biasedLevenshtein(String x, String y) {
        int[][] dp = new int[x.length() + 1][y.length() + 1];

        for(int i = 0; i <= x.length(); i++){
            for(int j = 0; j <= y.length(); j++){
                if(i == 0){
                    dp[i][j] = j;
                }else if(j == 0){
                    dp[i][j] = i;
                }else{
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j - 1]
                                    + (x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1),
                            dp[i - 1][j] + 1),
                            dp[i][j - 1] + 1);
                }
            }
        }

        int output = dp[x.length()][y.length()];
        if (y.startsWith(x) || x.startsWith(y)) {
            return output / 3f;
        }
        if (y.contains(x) || x.contains(y)) {
            return output / 1.5f;
        }
        return output;
    }

    public static float biasedLevenshteinInsensitive(String x, String y) {
        return biasedLevenshtein(x.toLowerCase(), y.toLowerCase());
    }

    public static float biasedLevenshteinLengthIndependent(String x, String y) {
        if (x.length() > y.length()){
            String temp = x;
            x = y;
            y = temp;
        }
        int xl = x.length(), yl = y.length();
        int yw = yl + 1;
        int[] dp = new int[2 * yw];

        for(int j=0; j <= yl; ++j){
            dp[j] = 0; // Insertions at the beginning are free
        }
        int prev = yw, curr = 0, temp;
        for(int i = 1; i <= xl; i++){
            temp = prev;
            prev = curr;
            curr = temp;
            dp[curr] = i;
            for(int j = 1; j <= yl; j++){
                dp[curr + j] = Math.min(dp[prev + j - 1] + (x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1),
                        Math.min(dp[prev + j], dp[curr + j - 1]) + 1);
            }
        }

        // startsWith
        if(dp[curr + xl] == 0) return 0f;
        // Disregard insertions at the end - if it made it it made it
        int output = xl;
        for(int i = curr; i < curr + yl; ++i){
            output = Math.min(output, dp[i]);
        }
        // contains
        if (output == 0) return 0.5f; // Prefer startsWith
        return output;
    }

    public static float biasedLevenshteinLengthIndependentInsensitive(String x, String y) {
        return biasedLevenshteinLengthIndependent(x.toLowerCase(), y.toLowerCase());
    }
}
