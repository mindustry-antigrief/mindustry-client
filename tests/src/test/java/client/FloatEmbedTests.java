package client;

import arc.math.Mathf;
import arc.math.geom.Vec2;
import mindustry.client.FooUser;
import mindustry.client.utils.FloatEmbed;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FloatEmbedTests {

    /** Tests the float embedding system. */
    @Test
    static void testFloatEmbed() {
        byte[] temp = new byte[1];
        Mathf.rand.nextBytes(temp);
        byte item = temp[0];

        float input = (float) (Mathf.rand.nextDouble() * 10000);

        float embedded = FloatEmbed.embedInFloat(input, item);
        Assertions.assertTrue(Math.abs(embedded - input) < 0.1);
        Assertions.assertTrue(FloatEmbed.isEmbedded(embedded, item));

        Vec2 inputVector = new Vec2((float) (Mathf.rand.nextDouble() * 10000), (float) (Mathf.rand.nextDouble() * 10000));
        Vec2 notAssisting = FooUser.encode(inputVector.x, inputVector.y, false);
        Vec2 assisting = FooUser.encode(inputVector.x, inputVector.y, true);

        Assertions.assertTrue(notAssisting.dst(inputVector) < 0.1f);
        Assertions.assertTrue(assisting.dst(inputVector) < 0.1f);

        Assertions.assertTrue(FooUser.isUser(notAssisting));
        Assertions.assertTrue(FooUser.isUser(assisting));

        Assertions.assertFalse(FooUser.isAssisting(notAssisting));
        Assertions.assertTrue(FooUser.isAssisting(assisting));
    }
}
