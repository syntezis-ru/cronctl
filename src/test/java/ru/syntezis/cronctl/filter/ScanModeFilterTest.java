package ru.syntezis.cronctl.filter;

import org.junit.jupiter.api.Test;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.enums.ScanType;
import ru.syntezis.cronctl.properties.CronctlProperties;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanModeFilterTest {

    private ScanModeFilter underTest;

    @Test
    void predicate_AutoModeRegularMethod_MethodPasses() throws NoSuchMethodException {
        // Given
        underTest = new ScanModeFilter(scanWith(ScanType.AUTO));
        final Method method = Methods.class.getDeclaredMethod("plain");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isTrue();
    }

    @Test
    void predicate_AutoModeExcludedMethod_MethodFiltered() throws NoSuchMethodException {
        // Given
        underTest = new ScanModeFilter(scanWith(ScanType.AUTO));
        final Method method = Methods.class.getDeclaredMethod("withExclude");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isFalse();
    }

    @Test
    void predicate_AutoModeMethodWithCronctlTask_MethodPasses() throws NoSuchMethodException {
        // Given
        underTest = new ScanModeFilter(scanWith(ScanType.AUTO));
        final Method method = Methods.class.getDeclaredMethod("withCronctlTask");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isTrue();
    }

    @Test
    void predicate_AnnotatedModeMethodWithCronctlTask_MethodPasses() throws NoSuchMethodException {
        // Given
        underTest = new ScanModeFilter(scanWith(ScanType.ANNOTATED));
        final Method method = Methods.class.getDeclaredMethod("withCronctlTask");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isTrue();
    }

    @Test
    void predicate_AnnotatedModeRegularMethod_MethodFiltered() throws NoSuchMethodException {
        // Given
        underTest = new ScanModeFilter(scanWith(ScanType.ANNOTATED));
        final Method method = Methods.class.getDeclaredMethod("plain");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isFalse();
    }

    @Test
    void predicate_AnnotatedModeExcludedMethod_MethodFiltered() throws NoSuchMethodException {
        // Given
        underTest = new ScanModeFilter(scanWith(ScanType.ANNOTATED));
        final Method method = Methods.class.getDeclaredMethod("withExclude");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isFalse();
    }

    // CronctlTask.Exclude is ignored in ANNOTATED mode — explicit @CronctlTask wins
    @Test
    void predicate_AnnotatedModeMethodWithCronctlTaskAndExclude_MethodPasses() throws NoSuchMethodException {
        // Given
        underTest = new ScanModeFilter(scanWith(ScanType.ANNOTATED));
        final Method method = Methods.class.getDeclaredMethod("withCronctlTaskAndExclude");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isTrue();
    }

    @Test
    void predicate_PackageModeMethodInListedPackage_MethodPasses() throws NoSuchMethodException {
        // Given
        underTest = new ScanModeFilter(scanWithPackages(Methods.class.getPackageName()));
        final Method method = Methods.class.getDeclaredMethod("plain");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isTrue();
    }

    @Test
    void predicate_PackageModeMethodInSubpackage_MethodPasses() throws NoSuchMethodException {
        underTest = new ScanModeFilter(scanWithPackages("ru.syntezis.cronctl"));
        final Method method = Methods.class.getDeclaredMethod("plain");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isTrue();
    }

    @Test
    void predicate_PackageModeMethodNotInPackage_MethodFiltered() throws NoSuchMethodException {
        // Given
        underTest = new ScanModeFilter(scanWithPackages("ru.syntezis.cronctl.filter"));
        final Method method = String.class.getDeclaredMethod("length");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isFalse();
    }

    @Test
    void predicate_PackageModeExcludedMethodInPackage_MethodFiltered() throws NoSuchMethodException {
        // Given
        underTest = new ScanModeFilter(scanWithPackages(Methods.class.getPackageName()));
        final Method method = Methods.class.getDeclaredMethod("withExclude");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isFalse();
    }

    @Test
    void predicate_PackageModeEmptyBasePackages_MethodFiltered() throws NoSuchMethodException {
        // Given
        underTest = new ScanModeFilter(scanWith(ScanType.PACKAGE));
        final Method method = Methods.class.getDeclaredMethod("plain");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isFalse();
    }

    @Test
    void predicate_PackageModeMultiplePackages_MethodInOneOfThemPasses() throws NoSuchMethodException {
        // Given
        underTest = new ScanModeFilter(scanWithPackages("com.example", Methods.class.getPackageName()));
        final Method method = Methods.class.getDeclaredMethod("plain");

        // When
        final boolean actual = underTest.predicate().test(method);

        // Then
        assertThat(actual)
                .isTrue();
    }

    private CronctlProperties.Scan scanWith(ScanType type) {
        CronctlProperties.Scan scan = new CronctlProperties.Scan();
        scan.setType(type);
        return scan;
    }

    private CronctlProperties.Scan scanWithPackages(String... packages) {
        CronctlProperties.Scan scan = new CronctlProperties.Scan();
        scan.setType(ScanType.PACKAGE);
        scan.setBasePackages(List.of(packages));
        return scan;
    }

    private static class Methods {

        public void plain() {}

        @CronctlTask
        public void withCronctlTask() {}

        @CronctlTask.Exclude
        public void withExclude() {}

        @CronctlTask
        @CronctlTask.Exclude
        public void withCronctlTaskAndExclude() {

        }
    }
}
