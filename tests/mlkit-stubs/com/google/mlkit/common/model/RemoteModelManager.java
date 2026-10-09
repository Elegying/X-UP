package com.google.mlkit.common.model;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Only records SDK requests; tests decide when each independent operation finishes. */
public final class RemoteModelManager {
    private static final RemoteModelManager instance = new RemoteModelManager();
    public final List<Task<Set<TranslateRemoteModel>>> inventories = new ArrayList<>();
    public final List<Task<Void>> downloads = new ArrayList<>();
    public final List<String> languages = new ArrayList<>();
    public static RemoteModelManager getInstance() { return instance; }
    public Task<Set<TranslateRemoteModel>> getDownloadedModels(Class<TranslateRemoteModel> type) {
        Task<Set<TranslateRemoteModel>> task = new Task<>();
        inventories.add(task);
        return task;
    }
    public Task<Void> download(TranslateRemoteModel model, DownloadConditions conditions) {
        Task<Void> task = new Task<>();
        downloads.add(task);
        languages.add(model.getLanguage());
        return task;
    }
    public Task<Boolean> isModelDownloaded(TranslateRemoteModel model) {
        Task<Boolean> task = new Task<>();
        task.succeed(true);
        return task;
    }
    public void reset() { inventories.clear(); downloads.clear(); languages.clear(); }
}
