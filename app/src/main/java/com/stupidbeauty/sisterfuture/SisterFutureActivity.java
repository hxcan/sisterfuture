boolean hasImage = (currentImageBase64 != null && !currentImageBase64.isEmpty())
        || (currentImageRemoteUrl != null && !currentImageRemoteUrl.isEmpty());
    boolean hasVideo = (currentVideoPath != null && !currentVideoPath.isEmpty()
        && currentVideoRemoteUrl != null && !currentVideoRemoteUrl.isEmpty());