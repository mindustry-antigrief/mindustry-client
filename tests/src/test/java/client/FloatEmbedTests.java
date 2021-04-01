package client;

import arc.math.*;
import arc.math.geom.*;
import mindustry.client.*;
import mindustry.client.utils.*;
import org.junit.jupiter.api.*;

public class FloatEmbedTests {

    /** Tests the float embedding system. */
    @Test
    void testFloatEmbed() {
        byte[] temp = new byte[1];
        Mathf.rand.nextBytes(temp);
        byte item = temp[0];

        float input = (float) (Mathf.rand.nextDouble() * 8000);

        float embedded = FloatEmbed.embedInFloat(input, item);
        Assertions.assertTrue(Math.abs(embedded - input) < 1f);
        Assertions.assertTrue(FloatEmbed.isEmbedded(embedded, item));

        Vec2 inputVector = new Vec2((float) (Mathf.rand.nextDouble() * 8000), (float) (Mathf.rand.nextDouble() * 8000));
        Vec2 notAssisting = new Vec2(FloatEmbed.embedInFloat(inputVector.x, ClientVars.FOO_USER), FloatEmbed.embedInFloat(inputVector.y, ClientVars.FOO_USER));
        Vec2 assisting = new Vec2(FloatEmbed.embedInFloat(inputVector.x, ClientVars.FOO_USER), FloatEmbed.embedInFloat(inputVector.y, ClientVars.ASSISTING));

        Assertions.assertTrue(notAssisting.dst(inputVector) < 1f);
        Assertions.assertTrue(assisting.dst(inputVector) < 1f);

        Assertions.assertTrue(FloatEmbed.isEmbedded(notAssisting.x, ClientVars.FOO_USER));
        Assertions.assertTrue(FloatEmbed.isEmbedded(assisting.x, ClientVars.FOO_USER));

        Assertions.assertFalse(FloatEmbed.isEmbedded(notAssisting.y, ClientVars.ASSISTING));
        Assertions.assertTrue(FloatEmbed.isEmbedded(assisting.y, ClientVars.ASSISTING));
    }
}
