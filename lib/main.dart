import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:logging/logging.dart';
import 'ui/home_ui.dart';
import 'theme/config/theme.dart';
import 'theme/theme_mode_state.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize Hive
  await Hive.initFlutter();
  await Hive.openBox('prefs');

  // Logging setup
  if (kReleaseMode) {
    Logger.root.level = Level.WARNING;
  }
  Logger.root.onRecord.listen((record) {
    debugPrint('${record.level.name}: ${record.time}: '
        '${record.loggerName}: ${record.message}');
  });

  runApp(const ProviderScope(child: MyApp()));
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