//©2025 1510ty
//MIT License
package com.mc1510ty.SyncBackupPlugin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Main extends JavaPlugin {

    Path langFolder = getDataFolder().toPath().resolve("lang");
    String language;
    private FileConfiguration lang;
    long initialDelayTicks = 590L * 20; //10分 (9分50秒)
    long loopIntervalTicks = 3590L * 20; //60分 (59分50秒)
    long warningDelay = 10L * 20;
    private int zipCompressionLevel = 1;

    @Override
    public void onEnable() {
        //怒涛のconfigラッシュ
        saveDefaultConfig(); //これだけBukkitAPI
        saveDefaultLang();
        loadConfig();
        Loadlang();
        LoadCommand();
        updateConfig();
        //バックアップタスクを開始！名前は適当()
        crontask();
    }

    @Override
    public void onDisable() {
        getLogger().info("Disable.");
    }

    private void loadConfig() {

        // 言語読み込み + フォールバック
        language = getConfig().getString("language");
        if (language == null || language.isEmpty()) {
            language = "en_US";
            getLogger().info("Language was empty, fallback to default: " + language);
        }

        // 初期待機
        long value = getConfig().getLong("initialDelaySeconds", -1);
        if (value <= 0) {
            getLogger().warning("Initial delay time not set. Using default.");
            value = 590; // 秒
        }
        initialDelayTicks = Math.max(value - 10, 0) * 20;

        // インターバル
        value = getConfig().getLong("loopIntervalSeconds", -1);
        if (value <= 0) {
            getLogger().warning("Interval time not set. Using default.");
            value = 3590; // 秒
        }
        loopIntervalTicks = Math.max(value - 10, 0) * 20;

        int compressionLevel = getConfig().getInt("zipCompressionLevel", 1);
        if (compressionLevel < 0 || compressionLevel > 9) {
            getLogger().warning("Invalid zipCompressionLevel. Allowed range is 0–9. Reverting to default: 1.");
            compressionLevel = 1;
        }
        this.zipCompressionLevel = compressionLevel;

    }

    private void saveDefaultLang()  {
        try {
            //langフォルダ自体が存在しない場合、またはディレクトリでない場合は、
            //ディレクトリの削除後(languageがディレクトリ以外の場合)、ディレクトリの作成とデフォルト言語の配置。
            if (Files.notExists(langFolder) || !Files.isDirectory(langFolder)) {
                if (!Files.isDirectory(langFolder) && Files.exists(langFolder)) {
                    Files.delete(langFolder);
                }
                Files.createDirectories(langFolder);
                saveResource("lang/en_US.yml", false);
                saveResource("lang/ja_JP.yml", false);
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            SendError("error.backupfoldercreatefailed.server", "error.explanation1" , "error.backupfoldercreatefailed.player" ,"IOException");
            return;
        }


        //ファイルが1つもない場合は実行。ただし、ここでは指定している言語ファイルが存在するかのチェックはしていない。
        try {
            // フォルダが空かどうかをチェック
            boolean isEmpty = true;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(langFolder)) {
                isEmpty = !stream.iterator().hasNext();
            }
            // 空の場合のみデフォルト言語ファイルを展開
            if (isEmpty) {
                    saveResource("lang" + File.separator + language + ".yml", false);
            }
            return;
        } catch (IOException e) {
            e.printStackTrace();
            SendError("error.config.savedefaultlang.server", "error.explanation1" , "error.config.savedefaultlang.player" ,"IOException");
            return;
        }
    }

    private void Loadlang() {

        File langFile = new File(getDataFolder(), "lang" + File.separator + language + ".yml");
        if (!langFile.exists()) {
            // デフォルトファイルを保存

            saveResource("lang" + File.separator + language + ".yml", false); // false = 上書きしない
            getLogger().info("Language file not found. Created default: " + language + ".yml");

        }
        lang = YamlConfiguration.loadConfiguration(langFile);

    }

    private void LoadCommand() {
        LiteralArgumentBuilder<CommandSourceStack> sbpCommand = Commands.literal("sbp")
                .then(Commands.literal("backup")
                        .then(Commands.literal("now")
                            .executes(ctx -> {
                                backup();
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                );
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(sbpCommand.build());
        });
    }

    private void updateConfig() {
        FileConfiguration defaultConfig;
        try (InputStream is = getResource("config.yml")) {
            if (is == null) return; // デフォルト config が無い場合は終了
            defaultConfig = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(is));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        boolean updated = addMissingKeys(defaultConfig, getConfig());

        if (updated) {
            saveConfig();
            getLogger().info("Config updated with missing default values.");
        }
    }

    private boolean addMissingKeys(FileConfiguration source, FileConfiguration target) {
        boolean updated = false;
        for (String key : source.getKeys(true)) {
            if (target.get(key) == null) {
                target.set(key, source.get(key));
                getLogger().info("Added missing config key: " + key + " = " + source.get(key));
                updated = true;
            }
        }
        return updated;
    }


    public void crontask() {

        //多分忘れるから解説書いておきます。

        //※Lはおそらく秒って意味。デフォルトだとミリ秒なんじゃね？20はそのままtick。
        //上記訂正。Lは秒って意味じゃなくlongって意味みたいです。明示的にlongを指定することで、intになるのを防ぐ。
        //ちなみになぜtickのほうにはLがないかというと、秒のほうがLだとtickのほうもLに昇格するらしい

        //Globalなんっちゃらはリージョンに依存しない処理を実行する場合に、Folia対応するさいに囲むだけ。これだけ。
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,
                (task) -> {

                    Bukkit.getServer().broadcast(Component.text(Objects.requireNonNull(lang.getString("message.backup.start_10mae")), NamedTextColor.YELLOW));
                    //↓これは10秒待機のやつ。
                    Bukkit.getGlobalRegionScheduler().runDelayed(this, t -> {

                        backup(); //バックアップ開始！Folia対応分で下囲んでないから大丈夫？とか思うかもしれないけど、実際のコードとしては、backup();の内容が丸々ここに来るイメージ。まあつまり気にしなくてOKってこと。
                    }, warningDelay);//ここは10秒待機って意味ね。その後バックアップ開始!

                }, initialDelayTicks, loopIntervalTicks
                //上の謎の変数はこれはrunAtFixedRateの引数指定。初期待機時間と周回待機時間の指定。.getGlobalRegionSchedulerの引数指定ではないことに注意。
        );
    }

    private void backup(){

        Bukkit.getServer().broadcast(Component.text(Objects.requireNonNull(lang.getString("message.backup.start")), NamedTextColor.YELLOW));

        //zip化や自動作成などはChatGPT製。
        //BukkitAPIはGPT苦手っぽい
        //Javaはさすがに書けるらしい(知らんけどー)

        Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> {

            Path backupsDir = Paths.get("backups"); //パス設定。あとでconfigで変えられるようにしてもいいかもしれない

            //バックアップ先フォルダ作成。
            try {
                if (!Files.exists(backupsDir)) { //バックアップフォルダが存在するか確認。
                    Files.createDirectories(backupsDir);
                }
            } catch (IOException e) {
                e.printStackTrace();
                SendError("error.backupfoldercreatefailed.server", "error.explanation1", "error.backupfoldercreatefailed.player", "IOException");
                CleanUp();
                return;
            }

            int maxNumber = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupsDir, "*.zip")) { // *.zip に限定
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) { // ファイルかどうか確認
                        String fileName = path.getFileName().toString();
                        // 拡張子を除去
                        if (fileName.endsWith(".zip")) {
                            String numberPart = fileName.substring(0, fileName.length() - 4); // ".zip" を削除
                            try {
                                int num = Integer.parseInt(numberPart);
                                if (num >= maxNumber) maxNumber = num + 1;
                            } catch (NumberFormatException ignored) {
                                // 数字じゃないファイルは無視
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                SendError("error.nowbackupfoldernumberfailed.server", "error.explanation1", "error.nowbackupfoldernumberfailed.player", "IOException");
                return;
            }

            Path currentBackupDir = backupsDir.resolve(String.valueOf(maxNumber));

            try {
                Files.createDirectories(currentBackupDir);
            } catch (IOException e) {
                e.printStackTrace();
                SendError("error.nowbackupfoldercreatefailed.server", "error.explanation1", "error.nowbackupfoldercreatefailed.player", "IOException");
                CleanUp();
                return;
            }

            for (World world : Bukkit.getWorlds()) {
                //すべてのワールドのオートセーブを切り、保存。
                world.setAutoSave(false);
                world.save();

                Path source = world.getWorldFolder().toPath();
                Path target = currentBackupDir.resolve(world.getName());

                copyFolder(source, target);

            }

            Bukkit.getServer().broadcast(Component.text(Objects.requireNonNull(lang.getString("message.backup.copysuccess")), NamedTextColor.GREEN));

            for (World world : Bukkit.getWorlds()) {
                world.setAutoSave(true);
            }

            int finalMaxNumber = maxNumber;
            Bukkit.getAsyncScheduler().runNow(this, scheduledTask -> {
                //zip化を非同期で実行。

                Path source = currentBackupDir;
                Path target = backupsDir.resolve(finalMaxNumber + ".zip");

                zipWorldFolder(source, target);
                deleteFolder(source);

                cleanupOldBackups();

                Bukkit.getGlobalRegionScheduler().run(this, scheduledTask1 -> {
                    Bukkit.getServer().broadcast(Component.text(Objects.requireNonNull(lang.getString("message.backup.success")), NamedTextColor.GREEN));
                });

            });
        },1);
    }

    //これより下汎用
    public void copyFolder(Path source, Path target) {
        // source: コピー元フォルダ
        // target: コピー先フォルダ
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // コピー先に同じフォルダ構造を作る
                    Path targetDir = target.resolve(source.relativize(dir));
                    if (!Files.exists(targetDir)) {
                            Files.createDirectories(targetDir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String fileName = file.getFileName().toString();

                    // .lockファイルをスキップ
                    if (fileName.endsWith(".lock")) {
                        return FileVisitResult.CONTINUE;
                    }

                    // ファイルをコピー
                    Path targetFile = target.resolve(source.relativize(file));
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            SendError("error.copyfolderfailed.server", "error.explanation1" , "error.copyfolderfailed.player" ,"IOException");
            CleanUp();
            return;
        }
    }

    private void zipWorldFolder(Path sourceDir, Path zipFile) {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {

            zos.setLevel(zipCompressionLevel);

            int rootCount = sourceDir.getNameCount();

            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relativePath = sourceDir.relativize(file);
                    try (InputStream in = Files.newInputStream(file)) {
                        zos.putNextEntry(new ZipEntry(relativePath.toString()));
                        in.transferTo(zos);
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(sourceDir)) {
                        Path relativePath = sourceDir.relativize(dir);
                        zos.putNextEntry(new ZipEntry(relativePath.toString() + "/"));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            SendError("error.worldzippingfailed.server", "error.explanation1",
                    "error.worldzippingfailed.player", "IOException");
            CleanUp();
        }
    }

    private void deleteFolder(Path folder)  {
        if (!Files.exists(folder)) return;
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                        SendError("error.tempcopyworlddeletefailed.server", "error.explanation1" , "error.tempcopyworlddeletefailed.player" ,"IOException");
                        CleanUp();
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        Files.delete(dir);
                    } catch (IOException e) {
                        e.printStackTrace();
                        SendError("error.tempcopyworlddeletefailed.server", "error.explanation1" , "error.tempcopyworlddeletefailed.player" ,"IOException");
                        CleanUp();
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            SendError("error.tempcopyworlddeletefailed.server", "error.explanation1" , "error.tempcopyworlddeletefailed.player" ,"IOException");
            CleanUp();
            return;
        }
    }

    private void SendError(String ServerMessage, String ServerExplanation, String PlayerMessage, String Error) {

        Bukkit.getGlobalRegionScheduler().run(this, scheduledTask -> {
            Bukkit.getConsoleSender().sendMessage(Component.text(Objects.requireNonNull(lang.getString("error.backupfailed")), NamedTextColor.RED)//エラーメッセージ
                    .append(Component.text("\n"))
                    .append(Component.text(Objects.requireNonNull(lang.getString(ServerMessage)), NamedTextColor.RED))//失敗した内容
                    .append(Component.text("\n"))
                    .append(Component.text(Objects.requireNonNull(lang.getString(ServerExplanation)), NamedTextColor.RED))//原因説明
                    .append(Component.text("\n"))
                    .append(Component.text(Error, NamedTextColor.RED))//例外名
            );

            Bukkit.getServer().broadcast(Component.text(Objects.requireNonNull(lang.getString("causeplayer")) + " (" + (Objects.requireNonNull(lang.getString(PlayerMessage))) + ")", NamedTextColor.RED));//プレイヤー向け表示
        });
    }

    private void CleanUp() {
        Path backupsDir = Paths.get("backups");

        if (!Files.exists(backupsDir)) return;

        try {
            Files.list(backupsDir)
                    .forEach(path -> {
                        try {
                            // ".zip" ファイルは最後まで作られなかった場合を考慮して削除
                            if (Files.isRegularFile(path) && path.toString().endsWith(".zip")) {
                                Files.deleteIfExists(path);
                            }
                            // ディレクトリは一時コピー用の場合に削除
                            else if (Files.isDirectory(path)) {
                                deleteFolder(path); // 既に実装済みの deleteFolder() を利用
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            getLogger().warning("Failed to cleanup: " + path);
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
            SendError("error.cleanupfailed.server",
                    "error.explanation1",
                    "error.cleanupfailed.player",
                    "IOException");
        }
    }


    private void cleanupOldBackups() {

        int maxBackups = getConfig().getInt("maxBackups", 20);
        Path backupsDir = Paths.get("backups");

        if (maxBackups == -1) {
            return;
        }

        if (!Files.exists(backupsDir)) return;

        try {
            // zip のみ取得
            var backupFiles = Files.list(backupsDir)
                    .filter(p -> p.getFileName().toString().endsWith(".zip"))
                    .sorted((a, b) -> {
                        // 数字順に並べる
                        int na = Integer.parseInt(a.getFileName().toString().replace(".zip", ""));
                        int nb = Integer.parseInt(b.getFileName().toString().replace(".zip", ""));
                        return Integer.compare(na, nb);
                    })
                    .toList();

            if (backupFiles.size() <= maxBackups) return;

            // 削除すべき数
            int deleteCount = backupFiles.size() - maxBackups;

            for (int i = 0; i < deleteCount; i++) {
                Files.deleteIfExists(backupFiles.get(i));
                getLogger().info("Deleted old backup: " + backupFiles.get(i).getFileName());
            }

        } catch (Exception e) {
            e.printStackTrace();
            SendError("error.autodeletefailed.server",
                    "error.explanation1",
                    "error.autodeletefailed.player",
                    "IOException");

        }
    }

}
