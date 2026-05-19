package io.github.fantasticname.mybilibili.mq;

import org.junit.jupiter.api.*;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CouponGrabConsumer单元测试
 *
 * <p>覆盖优惠券码生成逻辑。</p>
 *
 * @author FantasticName
 */
class CouponGrabConsumerTest {

    /**
     * 通过反射测试generateCouponCode私有方法
     */
    @Nested
    @DisplayName("generateCouponCode 优惠券码生成测试")
    class GenerateCouponCodeTests {

        @Test
        @DisplayName("生成的优惠券码长度为16")
        void generateCouponCode_lengthIs16() throws Exception {
            CouponGrabConsumer consumer = CouponGrabConsumer.getInstance(null, null, null);
            Method method = CouponGrabConsumer.class.getDeclaredMethod("generateCouponCode");
            method.setAccessible(true);
            String code = (String) method.invoke(consumer);

            assertNotNull(code);
            assertEquals(16, code.length());
        }

        @Test
        @DisplayName("生成的优惠券码全部为大写字母和数字")
        void generateCouponCode_upperCaseAlphanumeric() throws Exception {
            CouponGrabConsumer consumer = CouponGrabConsumer.getInstance(null, null, null);
            Method method = CouponGrabConsumer.class.getDeclaredMethod("generateCouponCode");
            method.setAccessible(true);
            String code = (String) method.invoke(consumer);

            assertNotNull(code);
            assertTrue(code.matches("[A-Z0-9]+"), "优惠券码应全部为大写字母和数字");
        }

        @Test
        @DisplayName("每次生成的优惠券码不同")
        void generateCouponCode_unique() throws Exception {
            CouponGrabConsumer consumer = CouponGrabConsumer.getInstance(null, null, null);
            Method method = CouponGrabConsumer.class.getDeclaredMethod("generateCouponCode");
            method.setAccessible(true);
            String code1 = (String) method.invoke(consumer);
            String code2 = (String) method.invoke(consumer);

            assertNotEquals(code1, code2, "两次生成的优惠券码应该不同");
        }
    }
}
