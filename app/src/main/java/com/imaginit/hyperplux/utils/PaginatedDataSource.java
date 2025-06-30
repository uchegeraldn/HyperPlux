package com.imaginit.hyperplux.utils;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PageKeyedDataSource;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.imaginit.hyperplux.models.Asset;

import java.util.ArrayList;
import java.util.List;

/**
 * DataSource that loads assets from Firestore in pages
 */
public class PaginatedDataSource extends PageKeyedDataSource<DocumentSnapshot, Asset> {
    private static final String TAG = "PaginatedDataSource";
    private final FirebaseFirestore firestore;
    private final Query baseQuery;
    private final int pageSize;
    private final MutableLiveData<NetworkState> networkState;
    private final MutableLiveData<NetworkState> initialLoadState;
    private final String userId;

    public PaginatedDataSource(String userId, Query baseQuery, int pageSize) {
        this.userId = userId;
        this.firestore = FirebaseFirestore.getInstance();
        this.baseQuery = baseQuery;
        this.pageSize = pageSize;
        this.networkState = new MutableLiveData<>();
        this.initialLoadState = new MutableLiveData<>();
    }

    public MutableLiveData<NetworkState> getNetworkState() {
        return networkState;
    }

    public MutableLiveData<NetworkState> getInitialLoadState() {
        return initialLoadState;
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams<DocumentSnapshot> params,
                            @NonNull LoadInitialCallback<DocumentSnapshot, Asset> callback) {
        initialLoadState.postValue(NetworkState.LOADING);
        networkState.postValue(NetworkState.LOADING);

        // Create query for initial page
        Query query = baseQuery.limit(pageSize);

        query.get().addOnSuccessListener(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                initialLoadState.postValue(NetworkState.LOADED);
                networkState.postValue(NetworkState.LOADED);
                callback.onResult(new ArrayList<>(), null, null);
                return;
            }

            List<Asset> assets = processQuerySnapshot(querySnapshot);
            DocumentSnapshot lastVisible = querySnapshot.getDocuments().get(querySnapshot.size() - 1);

            // Check if there are more items to load
            baseQuery.startAfter(lastVisible).limit(1).get().addOnSuccessListener(checkSnapshot -> {
                DocumentSnapshot nextKey = checkSnapshot.isEmpty() ? null : lastVisible;
                initialLoadState.postValue(NetworkState.LOADED);
                networkState.postValue(NetworkState.LOADED);
                callback.onResult(assets, null, nextKey);
            }).addOnFailureListener(e -> {
                initialLoadState.postValue(new NetworkState.Error(e));
                networkState.postValue(new NetworkState.Error(e));
                callback.onResult(assets, null, null);
            });
        }).addOnFailureListener(e -> {
            initialLoadState.postValue(new NetworkState.Error(e));
            networkState.postValue(new NetworkState.Error(e));
            callback.onResult(new ArrayList<>(), null, null);
        });
    }

    @Override
    public void loadBefore(@NonNull LoadParams<DocumentSnapshot> params,
                           @NonNull LoadCallback<DocumentSnapshot, Asset> callback) {
        // Not implemented as we only paginate forward
        callback.onResult(new ArrayList<>(), null);
    }

    @Override
    public void loadAfter(@NonNull LoadParams<DocumentSnapshot> params,
                          @NonNull LoadCallback<DocumentSnapshot, Asset> callback) {
        networkState.postValue(NetworkState.LOADING);

        // Create query for next page
        Query query = baseQuery.startAfter(params.key).limit(pageSize);

        query.get().addOnSuccessListener(querySnapshot -> {
            if (querySnapshot.isEmpty()) {
                networkState.postValue(NetworkState.LOADED);
                callback.onResult(new ArrayList<>(), null);
                return;
            }

            List<Asset> assets = processQuerySnapshot(querySnapshot);
            DocumentSnapshot lastVisible = querySnapshot.getDocuments().get(querySnapshot.size() - 1);

            // Check if there are more items to load
            baseQuery.startAfter(lastVisible).limit(1).get().addOnSuccessListener(checkSnapshot -> {
                DocumentSnapshot nextKey = checkSnapshot.isEmpty() ? null : lastVisible;
                networkState.postValue(NetworkState.LOADED);
                callback.onResult(assets, nextKey);
            }).addOnFailureListener(e -> {
                networkState.postValue(new NetworkState.Error(e));
                callback.onResult(assets, null);
            });
        }).addOnFailureListener(e -> {
            networkState.postValue(new NetworkState.Error(e));
            callback.onResult(new ArrayList<>(), null);
        });
    }

    private List<Asset> processQuerySnapshot(QuerySnapshot querySnapshot) {
        List<Asset> assets = new ArrayList<>();
        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            Asset asset = document.toObject(Asset.class);
            if (asset != null) {
                // Increment view count locally, will be updated in database separately
                asset.setViews(asset.getViews() + 1);
                assets.add(asset);
            }
        }
        return assets;
    }

    /**
     * Network state class to track loading status
     */
    public static class NetworkState {
        public static final NetworkState LOADED = new NetworkState(Status.SUCCESS, null);
        public static final NetworkState LOADING = new NetworkState(Status.RUNNING, null);

        private final Status status;
        private final Throwable error;

        private NetworkState(Status status, Throwable error) {
            this.status = status;
            this.error = error;
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        public boolean isRunning() {
            return status == Status.RUNNING;
        }

        public boolean isFailed() {
            return status == Status.FAILED;
        }

        public Throwable getError() {
            return error;
        }

        public static class Error extends NetworkState {
            public Error(Throwable error) {
                super(Status.FAILED, error);
            }
        }

        public enum Status {
            RUNNING,
            SUCCESS,
            FAILED
        }
    }
}