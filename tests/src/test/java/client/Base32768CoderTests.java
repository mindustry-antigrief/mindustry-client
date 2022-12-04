package client;

import mindustry.client.communication.Base32768Coder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;

public class Base32768CoderTests {

    @Test
    void testCoder() throws IOException {
        for (int i = 0; i < 100; i++) {
            byte[] bytes = new byte[1_000];
            new Random().nextBytes(bytes);

            String encoded = Base32768Coder.INSTANCE.encode(bytes);
            Assertions.assertArrayEquals(bytes, Base32768Coder.INSTANCE.decode(encoded));
        }
//        Base32768Coder.INSTANCE.encode(new byte[] {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1});
//        Assertions.fail();
    }

    @Test
    void testKnown() throws IOException {
        byte[] input = Base64.getDecoder().decode("muy6VDsRHpGQBIH5GeyzOWelipFDompw2ocrwiV0kByRXJU5MAKU5kZuE8Ro5JQJXE2hTzaaeZHBQRe0keOIehjBY9T4HGcqgaomidiZlNpyXv7RTsUk+X1AQedlF96zBpoxTkc9BmUfgAy28jwsNTcc7TxxcVKvONaHga4KPb5CMCA+V9Q+xzT3YDBkeffDkZA59UrWXJKO9dmU3EF/hU0k529klQANhctVNoE2syo2gpuByQkUqRBMtzFEEwVStkxoazAyOi8Orwkxv+cjSTK7GSmhBFrCwSyprpx/4Aej5HBugCfhKH1clSA3bUUJZZ8ft8m2ihWcSv5cgZpSsk4KAtOcmXZDtHm41CiEZqICEz9r/CsiCwtDU+L0gl39ho26DdNAm9dLSlDYdMcRDRKx2B85wFIRcfixFdnrbPUqDjyJ9WWErgsyAtEhQOY971EkIENkfWSivFGOdzRXo7JqpaTX7mM5dAfEF9mFwZ/J4VP7IfK9/2ZLQgn7EDnhpn58BNN6FaQ4Nix6am/dLySHyt+fkf19+pSLavTC7RIkMaE2RY+iYL6xs2dHU8OQWBmirswsPZCOWIxl2OElQ17TI2MP1G8poLdTSQbYNmMQ5/gEevQJ0seOocd+s16EGs8YZRVgJaAtYill4FR6NszMwe3C9E0xP6Rg96IUPFswpcMvwhWkn+cKCXs1wo5rX5sQzH257tIAhO55ZfEGZhA8s9WCD5oPBn0rOgU6UXlxzhxTywLsWQ1yPTfKTVJrOX6p4EYciIj0917LdwiYGf6SdA6QifC62rU7xTreCHjDSGjqA0xKX88BZu5WTLEJ1B0aEvLThlLOFD05ATBlu9apvY8yjHW/6wlulI4n5TIZUoXn3RlkyMgcKd7PYZZdMW9p6FJUHFRN28d0tH/dQcS3w7F/OXtmlz44tTdsE6SkBLanYmLMnjHfS9R8ThXuH8Iw0My7ye6BuEnWM/reVJai3upnh9eEhC8Um0CCsXRMZoD8++gQp71QHJye0T1wf8ycg+yDOctqeXbfgG5lFLwBFv4G7exF+d/zB7jLoQZvH3c+ffSsMbmvZ0BczESquZDhkS4M/KjUq3Xvpl3LJoZwqz0rTsGdfY2zxBxLpendND0TducHwGWRxVbwsGOeejvOAgvrA09y6LF3qbBxC5kLhccj0WLlsv8t0AJMkY0UYXkqtyaOZY3TY6cRipA7sFRja0Ix6+86wtzZq0pJnLI1pC7LioocdJEiewZsQH256ArGIvpmp2ybfvmgJl/2x99+vWkLg/oBV0qVYMOLOaD/2ZPWLCnoLq/DxPoPlRFXButTtlOK4Q==");
        String output = new String(Base64.getDecoder().decode("4K2h5rSn4reO46K35YaV5Imy54a+5aGh5qKu5aOT5L+Y572M4aqP54en5aKw4peKx5fgobTikq7nmavnubfnvZjgqJfntZDhr77kvZnmqJrjhpfErOOeveGCn8624ou75qai4qOx5bOU5I2s5Jy05p6s4pak5air2Lnjtavgv5/jkqPgs63mspXkop3hiIrkn47jn43njqzni6jil5bho57knIrktJHigoTntLfiuJfhmq7mk7rmh7HktIXnhovlj6DkmZ7hn4Xjlbfnt6DEguG5p+G7uuaFh+WxguC6qtuZ5oW45bi54byJ5ba05LGT54au4bqg5aWb45Cv5ZCw4byV54Sr5LaM556s57ay45+e4ayV476q4Z6G5oiR546h4Yyq5pui55KF45ms4LOu56uW47u9472u4ai85baI57CM57ug57u355qi3a3iubzngoTipKXdpuWxsOG7neaYtc654KGZ44uy0b7hkZfhkZrep+W8qOGEp+ehkNGz5o604Z+E4YOW4p2Q4aCK1IThgK/iqJ7hnbfkp6rlsYrgtb7ilavIuOGRneONr9yG57Kj47mD4ZCF5qq+5b+L47Oj4K6y466T5K+q4ZSA1anhsLbjpLXiu7zmuJrnqYvjrpfhnbjlg7Hkga7nlLTjoI7lhrfihaLWpea2veSzm+SuruS/oeWQveKDsOKbhuWIluO1u+WUoeGmjOKhpeKmnOKYuue/mOSfm+abkea/o+avlOaYu8ug55Wk54Sh5pas5auw1LzgvI3gqZThp6Ljp5ngrrfntbDgp4vFk+OTteSPiOGFseavuOW6qeKvk+Gfm+KLvN+I4Yu045G94oug5bC455ms4bye4oqi4o6O4qmg54295Kis5Yuq5Y+84ruH4ZuDx7blkYvhsrjmmYfljIvig5Pil6fkiJ/ktofKj+agq+SFsuOOsOW+kOS+rOKGu+amgOOpruGlu+azg+WtvOKlpuacuMue55CF4pSf0qvgtL/WruSYs+SMh+O5qOWKsOOGv+akmuOei+aaj+a1jOC/hueileGUsuK3ouSvgOWYgOSOqOK1seWEg+K1l+aQv+C8oeKYj+WCp+KRl+iAgOaKnOC4mM+s5Y+J5Ieu47Sm57yj45qw5auk5YWX54SS5pmY44aY5Iq55oms5rWC45OV4ZqG5omI5J+T5p2O562G4Y6F5aW64p2I4LOo4KaS5I2t5Zmp5Yqt54GU4oGX55Gy54y34YuD5bav5ZWf44mp5Iix5auD5r+CxrTjvr7miKbig7PioazlqpDnm6TloL/ko7ziqb3kqaHOv+aal+KEvuSDvOaeruewmOWLq+aspeSfpOWHnueQueSWmOGTl+G/meKIsuKSoOW8ouGlt+Cqh+K2kuabgOKwguONguCpteGzueWUqOWvp+W4nuGaouWzvuKmiOOpgOOCvuSth+CjqOSzseGujuWMlOawpeSEm+GwpuOdqOaytOKZn+W8kOWVuNih4oqL56OW57iv4YSZ5qqg4YSM45aK47Wc5JC0442s5Luy5YKW4pWg5KuW4oOm576u4bOK4ZKr4p2a57i+5amx4oas5a+R4YKb5bSV5YW64oCE55KB5J6G55O856KB5Lq/4qiuy5nmrovgoqLhjJrln6Pli4znkJHjiL/lupLjsrrhiZHjjoPgto3iuJPMqeSSk+a7ouSGsuKXiOGEkeeCueKEpuGWm+Ocs+a2guK3lOayruWCgOa0kuOpm+KckuiAheOkguabk+egruSmqOWti+e3kuSinOSQkeeRr+SIkeOugeePj+ChmOGZteGCn+SKsOevvOOiqOOyjeC3qOWZp+WzlOG6uOG1rearrueEsOOgkcWL4pGw5IiZ4pCe44eO4La0562M4qS+4bu24qKI47uf5oyS5YeO47m95qmJ5LSm4biJ5JWR4oOq45CV56Kc5KCp5o6F5JGQ4bq455yS5YOF5KWg4au54but45yF5K2i5KeA4Lac1bHijrfhlabmgoXllaTgraTJieK8kueEieSQleeFmuSVlOSWjuKzlOGbuuGbp+Sbu+SFvOGChOO2o+azhOWMoeK9i8Kz0ag="));
        Assertions.assertEquals(Base32768Coder.INSTANCE.encode(input), output);
        Assertions.assertArrayEquals(Base32768Coder.INSTANCE.decode(output), input);
    }
}
