# CameraXView
[![](https://jitpack.io/v/chintan369/CameraXView.svg)](https://jitpack.io/#chintan369/CameraXView)

This library allows you to implement Camera functionality of capturing a picture, recording a video or get a bitmap frame for a snapshot to use it in other usage. This library is built on top of the Android's CameraX API.

CameraX library is very easy to implement and access all the features and functionality to use with no hassle of lots of codes. It gives all the functionality from setting faceing and flash options to recording video for a specific duration. Once you will use this library, you will love :heart: to use its features and functionality.

## Add Dependency
Use Gradle:

**Step 1:** Add it in your root _`build.gradle`_ at the end of repositories:
```gradle
allprojects {
    repositories {
      ...
      maven { url 'https://jitpack.io' }
    }
}
```

**Note:** In New Android studio updates, now `allProjects` block has been removed from root `build.gradle` file. So you must add this repository to your root _`settings.gradle`_ as below:
```gradle
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
      ...
      maven { url "https://jitpack.io" }
  }
}
```

**Step 2:** Add the dependency in your module's (e.g. app or any other) _`build.gradle`_ file:
```gradle
dependencies {
    ...
    implementation 'com.github.chintan369:CameraXView:<latest-version>'
}
```

## How do I use CameraX?
Add `CameraXView` in your activity/fragment's `layout` file:

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    ...
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
        <com.creative.camerax.CameraXView
            android:id="@+id/cameraXView"
            android:layout_width="0dp"
            android:layout_height="0dp"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent" />
    
</androidx.constraintlayout.widget.ConstraintLayout>
```

After adding a `CameraXView` to your layout file in activity/fragment, to capture a picture or record a video, you can implement functionality in your java/kotlin file.

## Implementation



