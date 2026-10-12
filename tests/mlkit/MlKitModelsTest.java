package io.github.jared.xlowerseek;

import android.content.Context;
import android.content.ContentResolver;
import android.os.Handler;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import java.util.HashSet;
import java.util.Set;

public final class MlKitModelsTest {
    private static final RemoteModelManager sdk = RemoteModelManager.getInstance();
    private static final Context app = new Context() {
        public ContentResolver getContentResolver() { return null; }
    };
    private static int checks, failures;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static Set<TranslateRemoteModel> models(String... languages) {
        Set<TranslateRemoteModel> result = new HashSet<>();
        for (String language : languages) result.add(new TranslateRemoteModel.Builder(language).build());
        return result;
    }
    private static void reset() {
        Handler.drain();
        sdk.reset();
        LocalModels.changes = 0;
        MlKitModels.checked = MlKitModels.ready = MlKitModels.downloading = false;
        MlKitModels.status = "checking";
    }
    private static void test(String name, Runnable test) {
        reset();
        try { test.run(); System.out.println("PASS " + name); }
        catch (AssertionError failure) { failures++; System.out.println("FAIL " + name + ": " + failure.getMessage()); }
        // Complete only already queued callbacks; pending SDK tasks are deliberately discarded.
        Handler.drain();
    }
    private static void completeDownload() {
        sdk.downloads.get(0).succeed(null);
        sdk.downloads.get(1).succeed(null);
        Handler.drain();
    }

    public static void main(String[] args) {
        test("late empty inventory cannot undo completed download", () -> {
            MlKitModels.refresh(app);
            int[] finished = {0};
            MlKitModels.download(app, () -> finished[0]++);
            check(sdk.languages.equals(java.util.List.of("zh", "ja")), "both required languages requested");
            completeDownload();
            check(MlKitModels.ready && !MlKitModels.downloading && finished[0] == 1, "download completes once");
            String readyStatus = MlKitModels.status;
            sdk.inventories.get(0).succeed(models()); Handler.drain();
            check(MlKitModels.ready, "stale inventory reverted ready after success");
            check(MlKitModels.status.equals(readyStatus) && LocalModels.changes == 1, "stale result changed catalog/status");
        });
        test("late inventory failure cannot replace download success", () -> {
            MlKitModels.refresh(app); MlKitModels.download(app, () -> {}); completeDownload();
            String readyStatus = MlKitModels.status;
            sdk.inventories.get(0).fail(new IllegalStateException("old query")); Handler.drain();
            check(MlKitModels.ready && MlKitModels.status.equals(readyStatus), "stale failure replaced successful status");
        });
        test("newest inventory wins out of order completion", () -> {
            MlKitModels.refresh(app); MlKitModels.refresh(app);
            sdk.inventories.get(1).succeed(models("zh", "ja")); Handler.drain();
            check(MlKitModels.ready && MlKitModels.checked, "new inventory accepted");
            sdk.inventories.get(0).succeed(models()); Handler.drain();
            check(MlKitModels.ready && LocalModels.changes == 1, "older inventory replaced newer state");
        });
        test("newest failure is preserved when older success arrives", () -> {
            MlKitModels.refresh(app); MlKitModels.refresh(app);
            sdk.inventories.get(1).fail(new IllegalStateException("latest failed")); Handler.drain();
            check(MlKitModels.checked && MlKitModels.status.contains("无法检查"), "current error is visible");
            sdk.inventories.get(0).succeed(models("zh", "ja")); Handler.drain();
            check(!MlKitModels.ready && MlKitModels.status.contains("无法检查"), "older result erased latest error");
        });
        test("inventory callbacks cannot interrupt active download state", () -> {
            MlKitModels.refresh(app); MlKitModels.download(app, () -> {});
            String activeStatus = MlKitModels.status;
            sdk.inventories.get(0).fail(new IllegalStateException("old failure")); Handler.drain();
            check(MlKitModels.downloading && MlKitModels.status.equals(activeStatus), "old error overwrote active progress");
        });
        test("refresh while downloading cannot later undo success", () -> {
            MlKitModels.download(app, () -> {}); MlKitModels.refresh(app); completeDownload();
            for (var query : sdk.inventories) query.succeed(models()); Handler.drain();
            check(MlKitModels.ready && LocalModels.changes == 1, "in-flight inventory undid download");
        });
        test("fresh inventory after download can report models removed", () -> {
            MlKitModels.download(app, () -> {}); completeDownload();
            MlKitModels.refresh(app);
            check(sdk.inventories.size() == 1, "inventory resumes after download");
            sdk.inventories.get(0).succeed(models()); Handler.drain();
            check(!MlKitModels.ready && LocalModels.changes == 2, "fresh removal is accepted and published");
        });
        test("current inventory detects missing language and avoids redundant publication", () -> {
            MlKitModels.refresh(app); sdk.inventories.get(0).succeed(models("zh", "ja")); Handler.drain();
            check(MlKitModels.ready && LocalModels.changes == 1, "ready transition published");
            MlKitModels.refresh(app); sdk.inventories.get(1).succeed(models("zh", "ja")); Handler.drain();
            check(LocalModels.changes == 1, "unchanged inventory does not publish");
            MlKitModels.refresh(app); sdk.inventories.get(2).succeed(models("zh")); Handler.drain();
            check(!MlKitModels.ready && LocalModels.changes == 2, "missing Japanese invalidates catalog");
        });
        test("failed download remains retryable and duplicate start does not schedule again", () -> {
            int[] finished = {0}, joined = {0};
            MlKitModels.download(app, () -> finished[0]++); MlKitModels.download(app, () -> joined[0]++);
            check(sdk.downloads.size() == 2, "duplicate download ignored");
            sdk.downloads.get(0).succeed(null); sdk.downloads.get(1).fail(new IllegalStateException("network")); Handler.drain();
            check(!MlKitModels.ready && !MlKitModels.downloading && finished[0] == 1, "failure releases active state");
            check(joined[0] == 1, "a recreated foreground service receives the existing task completion");
            check(MlKitModels.status.contains("下载失败") && LocalModels.changes == 0, "failure not advertised as ready");
            MlKitModels.download(app, () -> finished[0]++);
            sdk.downloads.get(2).succeed(null); sdk.downloads.get(3).succeed(null); Handler.drain();
            check(MlKitModels.ready && finished[0] == 2 && LocalModels.changes == 1, "retry publishes successful model state");
        });
        test("model verification failure keeps SDK cause instead of blaming network", () -> {
            MlKitModels.download(app, () -> {});
            sdk.downloads.get(0).succeed(null);
            sdk.downloads.get(1).fail(new java.util.concurrent.ExecutionException(new MlKitException("signed URL omitted",102)));
            Handler.drain();
            check(!MlKitModels.ready && !MlKitModels.downloading, "failed model remains retryable");
            check(MlKitModels.status.contains("校验") && MlKitModels.status.contains("102"), "SDK integrity failure must remain identifiable");
            check(!MlKitModels.status.contains("检查网络") && !MlKitModels.status.contains("signed URL"), "avoid wrong cause or raw SDK details");
        });
        test("SDK storage failure is actionable and identifies the missing language", () -> {
            MlKitModels.download(app, () -> {});
            sdk.downloads.get(0).fail(new MlKitException("storage failure",101)); sdk.downloads.get(1).succeed(null);
            Handler.drain();
            check(MlKitModels.status.contains("空间") && MlKitModels.status.contains("中文") && MlKitModels.status.contains("101"), "space failure identifies code and affected language");
            check(!MlKitModels.status.contains("检查网络"), "space failure is not a network failure");
        });
        if (failures > 0) throw new AssertionError("MlKitModels: " + failures + " failed cases");
        System.out.println("MlKitModels: " + checks + " assertions passed (SDK callback ordering, download/inventory races, retries)");
    }
}
