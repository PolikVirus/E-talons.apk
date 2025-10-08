import 'dart:io';

import 'package:etalons/ui/home_ui.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:logging/logging.dart';

// Uncomment these if you want Firebase Crashlytics later
// import 'package:firebase_core/firebase_core.dart';
// import 'package:firebase_crashlytics/firebase_crashlytics.dart';
// import 'firebase_options.dart';

import 'theme/config/theme.dart';
import 'theme/theme_mode_state.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // --- Optional Firebase Initialization ---
  // Only needed if you want crash reporting
  // if (!kIsWeb && (Platform.isIOS || Platform.isAndroid)) {
  //   try {
  //     await Firebase.initializeApp(
  //       options: DefaultFirebaseOptions.currentPlatform,
  //     );
  //     FlutterError.onError = FirebaseCrashlytics.instance.recordFlutterError;
  //   } catch (e) {
  //     debugPrint("Firebase couldn't be initialized: $e");
  //   }
  // }

  await Hive.initFlutter();
  await Hive.openBox('prefs');

  if (kReleaseMode) {
    Logger.root.level = Level.WARNING;
  }

  Logger.root.onRecord.listen((record) {
    debugPrint('${record.level.name}: ${record.time}: '
        '${record.loggerName}: '
        '${record.message}');
  });

  runApp(
    const ProviderScope(
      child: MyApp(),
    ),
  );
}

class MyApp extends ConsumerWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ThemeModeState currentTheme = ref.watch(themeProvider);
    return MaterialApp(
      title: 'Etalons',
      debugShowCheckedModeBanner: false,
      themeMode: currentTheme.themeMode,
      theme: lightTheme,
      darkTheme: darkTheme,
      home: const HomeUi(),
    );
  }
}