//©2025 1510ty
//MIT License
package com.mc1510ty.SyncBackupPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Main extends JavaPlugin {

    Path langFolder = getDataFolder().toPath().resolve("lang");
    String language;
    private FileConfiguration lang;

    @Override
    public void onEnable() {
        //怒涛のconfigラッシュ
        saveDefaultConfig(); //これだけBukkitAPI
        saveDefaultLang();
        loadConfig();
        Loadlang();
        //バックアップタスクを開始！名前は適当()
        crontask();
    }

    @Override
    public void onDisable() {
        getLogger().info("Disable.");
    }

    private void loadConfig() {
        //ここでlanguageを読み込み。
        language = getConfig().getString("language");
        //仮に 「language: 」とかなってたら、en_USにフォールバック。その他もen_USにフォールバック。
        if (language == null || language.isEmpty()) {
            language = "en_US";
            getLogger().info("Language was empty, fallback to default: " + language);
        }
    }

    private void saveDefaultLang()  {

        //langフォルダ自体が存在しない場合、またはディレクトリでない場合は、
        //ディレクトリの削除後(languageがディレクトリ以外の場合)、ディレクトリの作成とデフォルト言語の配置。
        if (Files.notExists(langFolder) || !Files.isDirectory(langFolder)) {
            try {
                if (!Files.isDirectory(langFolder)) {
                    Files.delete(langFolder);
                }
                Files.createDirectories(langFolder);
                saveResource("lang/en_US.yml", false);
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
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
        }
    }

    private void Loadlang() {

        File langFile = new File(getDataFolder(), "lang" + File.separator + language + ".yml");
        if (!langFile.exists()) {
            // デフォルトファイルを保存
            try {
                saveResource("lang" + File.separator + language + ".yml", false); // false = 上書きしない
                getLogger().info("Language file not found. Created default: " + language + ".yml");
            } catch (Exception e) {
                e.printStackTrace();
                getLogger().warning("Failed to save default language file!");
            }
        }
        lang = YamlConfiguration.loadConfiguration(langFile);

    }


    public void crontask() {

        //多分忘れるから解説書いておきます。

        //※Lはおそらく秒って意味。デフォルトだとミリ秒なんじゃね？20はそのままtick。
        //上記訂正。Lは秒って意味じゃなくlongって意味みたいです。明示的にlongを指定することで、intになるのを防ぐ。
        //ちなみになぜtickのほうにはLがないかというと、秒のほうがLだとtickのほうもLに昇格するらしい。
        long saisyonojuppunn = 590L * 20;
        long syuukizikann = 3600L * 20;
        long taikizikann = 10L * 20;

        //Globalなんっちゃらはリージョンに依存しない処理を実行する場合に、Folia対応するさいに囲むだけ。これだけ。
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,
                (task) -> {
                    //ifは多言語対応のため。
                    //↑古い。言語ファイル形式に移行するためこの方法はもう使わない。
                    if (language.equals("ja")) {
                        Bukkit.getServer().broadcast(Component.text("10秒後にバックアップを開始します！数秒固まります！", NamedTextColor.YELLOW));
                        //↓これは10秒待機のやつ。
                        Bukkit.getGlobalRegionScheduler().runDelayed(this, t -> {
                            backup(); //バックアップ開始！Folia対応分で下囲んでないから大丈夫？とか思うかもしれないけど、実際のコードとしては、backup();の内容が丸々ここに来るイメージ。まあつまり気にしなくてOKってこと。
                        }, taikizikann);//ここは10秒待機って意味ね。その後バックアップ開始!
                    }
                }, saisyonojuppunn, syuukizikann
                //上の謎の変数はこれはrunAtFixedRateの引数指定。初期待機時間と周回待機時間の指定。.getGlobalRegionSchedulerの引数指定ではないことに注意。
        );
    }

    private void backup(){

        //zip化や自動作成などはChatGPT製。

        if (language.equals("ja")) {
            Bukkit.getServer().broadcast(Component.text("バックアップを開始します!", NamedTextColor.YELLOW));
        }

        Path backupsDir = Paths.get("backups"); //パス設定。あとでconfigで変えられるようにしてもいいかもしれない

        //バックアップ先フォルダ作成。
        try {
            if (!Files.exists(backupsDir)) { //バックアップフォルダが存在するか確認。
                Files.createDirectories(backupsDir);
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (language.equals("ja")) {
                Bukkit.getServer().sendMessage(Component.text("バックアップに失敗しました", NamedTextColor.RED)
                        .append(Component.text("\n"))
                        .append(Component.text("バックアップ先フォルダの作成時にエラーが発生しました。", NamedTextColor.RED))
                        .append(Component.text("\n"))
                        .append(Component.text("読み書きの権限がない可能性があります。", NamedTextColor.RED))
                        .append(Component.text("\n"))
                        .append(Component.text("(IOException)", NamedTextColor.RED))
                );

                Bukkit.getServer().broadcast(Component.text("バックアップに失敗しました 管理者に確認してください (バックアップ先ディレクトリ作成失敗)", NamedTextColor.RED));

            }
            return;
        }

        int maxNumber = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupsDir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    try {
                        int num = Integer.parseInt(path.getFileName().toString());
                        if (num >= maxNumber) maxNumber = num + 1;
                    } catch (NumberFormatException ignored) {
                        //数字じゃないフォルダは無視
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (language.equals("ja")) {
                    Bukkit.getServer().sendMessage(Component.text("バックアップに失敗しました", NamedTextColor.RED)
                        .append(Component.text("\n"))
                        .append(Component.text("フォルダの最大番号取得に失敗しました。", NamedTextColor.RED))
                        .append(Component.text("\n"))
                        .append(Component.text("読み書きの権限がない可能性があります。", NamedTextColor.RED))
                        .append(Component.text("\n"))
                        .append(Component.text("(IOException)", NamedTextColor.RED))
                );

                Bukkit.getServer().broadcast(Component.text("バックアップに失敗しました 管理者に確認してください (フォルダ内最大番号取得失敗)", NamedTextColor.RED));

            }
            return;
        }

        Path currentBackupDir = backupsDir.resolve(String.valueOf(maxNumber));

        try {
            Files.createDirectories(currentBackupDir);
        } catch (IOException e) {
            e.printStackTrace();
            if (language.equals("ja")) {
                Bukkit.getServer().sendMessage(Component.text("バックアップに失敗しました", NamedTextColor.RED)
                        .append(Component.text("\n"))
                        .append(Component.text("バックアップ用フォルダの作成に失敗しました。", NamedTextColor.RED))
                        .append(Component.text("\n"))
                        .append(Component.text("読み書きの権限がない可能性があります。", NamedTextColor.RED))
                        .append(Component.text("\n"))
                        .append(Component.text("(IOException)", NamedTextColor.RED))
                );

                Bukkit.getServer().broadcast(Component.text("バックアップに失敗しました 管理者に確認してください (バックアップ用フォルダ作成失敗)", NamedTextColor.RED));
                return;
            }
        }

        for (World world : Bukkit.getWorlds()) {
            world.setAutoSave(false);
            world.save();

            Path source = world.getWorldFolder().toPath();
            Path target = currentBackupDir.resolve(world.getName());

            copyFolder(source, target);

        }

        int finalMaxNumber = maxNumber;
        Bukkit.getAsyncScheduler().runNow(this, scheduledTask -> {
            //zip化を非同期で実行。

            Path source = currentBackupDir;
            Path target = backupsDir.resolve(finalMaxNumber + ".zip");

                zipWorldFolder(source,target);
                deleteFolder(source);

        });
    }

    //これより下汎用
    public static void copyFolder(Path source, Path target) {
        // source: コピー元フォルダ
        // target: コピー先フォルダ
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // コピー先に同じフォルダ構造を作る
                    Path targetDir = target.resolve(source.relativize(dir));
                    if (!Files.exists(targetDir)) {
                        try {
                            Files.createDirectories(targetDir);
                        } catch (IOException e) {
                            e.printStackTrace();
                            //要メッセージ実装
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // ファイルをコピー
                    Path targetFile = target.resolve(source.relativize(file));
                    try {
                        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void zipWorldFolder(Path sourceDir, Path zipFile) {
        try {
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
                int rootCount = sourceDir.getNameCount();
                try {
                    Files.walk(sourceDir)
                            .forEach(path -> {
                                try {
                                    Path relativePath = path.subpath(rootCount, path.getNameCount());
                                    String zipEntryName = Files.isDirectory(path) ? relativePath + "/" : relativePath.toString();
                                    zos.putNextEntry(new ZipEntry(zipEntryName));

                                    if (Files.isRegularFile(path)) {
                                        try (var in = Files.newInputStream(path)) {
                                            in.transferTo(zos); // 8KB単位でコピー → メモリ使用は一定
                                        }
                                    }

                                    zos.closeEntry();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    //aaaa
                                }
                            });
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
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
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        Files.delete(dir);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void senderror(String error, String Exception) {



    }

}
