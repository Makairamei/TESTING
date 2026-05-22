const fs = require('fs');
const path = require('path');

const pluginFiles = [
    "Allpornstream/src/main/kotlin/com/byayzen/AllpornstreamPlugin.kt",
    "Animekhor/src/main/kotlin/com/Animekhor/AnimekhorProvider.kt",
    "AnimexinProvider/src/main/kotlin/com/Animexin/AnimexinProvider.kt",
    "AZNude/src/main/kotlin/com/kraptor/AZNudePlugin.kt",
    "DailymotionProvider/src/main/kotlin/recloudstream/DailymotionPlugin.kt",
    "HentaiWorld/src/main/kotlin/com/byayzen/HentaiWorldPlugin.kt",
    "IdlixProvider/src/main/kotlin/com/idlix/IdlixProviderPlugin.kt",
    "JavGuru/src/main/kotlin/com/kraptor/JavGuruPlugin.kt",
    "Javtiful/src/main/kotlin/com/istarvin/JavtifulPlugin.kt",
    "KissKH/src/main/kotlin/com/byayzen/KissKHPlugin.kt",
    "Reelshort/src/main/kotlin/com/reelshort/ReelshortPlugin.kt",
    "Sulasok/src/main/kotlin/com/istarvin/SulasokPlugin.kt",
    "TwitchProvider/src/main/kotlin/recloudstream/TwitchPlugin.kt",
    "XSmoviebox/src/main/kotlin/com/XSmoviebox/XSmovieboxProvider.kt",
    "Youperv/src/main/kotlin/com/byayzen/YoupervPlugin.kt",
    "YunshanID/src/main/kotlin/com/yunshanid/YunshanIDProvider.kt"
];

const basePath = 'D:/APK/mod/repos/CSNEW';

pluginFiles.forEach(f => {
    const fullPath = path.join(basePath, f);
    if (!fs.existsSync(fullPath)) {
        console.log(`[NOT FOUND] ${f}`);
        return;
    }
    const content = fs.readFileSync(fullPath, 'utf8');
    const loadLine = content.split('\n').find(l => l.includes('fun load'));
    console.log(`${f.split('/')[0]}: ${loadLine ? loadLine.trim() : 'NONE'}`);
});
