    permissionManager = new PermissionManager(this, new PermissionManager.PermissionCallback() {
      @Override
      public void onAllPermissionsGranted() {
        FileLogger.d(TAG, "All permissions granted");
      }
      
      @Override
      public void onPermissionDenied(String permission) {
        FileLogger.w(TAG, "Permission denied: " + permission);
      }
      
      @Override
      public void onNotificationPermissionDenied() {
        FileLogger.w(TAG, "Notification permission denied");
      }

      @Override
      public void onNotificationPermissionPermanentlyDenied() {
        FileLogger.w(TAG, "Notification permission permanently denied, need to go to settings");
        permissionManager.showPermissionPermanentlyDeniedDialog();
      }
    });