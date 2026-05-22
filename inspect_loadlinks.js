const fs = require('fs');
const path = require('path');

const files = [
    "Allpornstream/src/main/kotlin/com/byayzen/Allpornstream.kt",
    "Animekhor/src/main/kotlin/com/Animekhor/Animekhor.kt",
    "AnimexinProvider/src/main/kotlin/com/Animexin/Animexin.kt",
    "AZNude/src/main/kotlin/com/kraptor/AZNude.kt",
    "DailymotionProvider/src/main/kotlin/recloudstream/DailymotionProvider.kt",
    "HentaiWorld/src/main/kotlin/com/byayzen/HentaiWorld.kt",
    "IdlixProvider/src/main/kotlin/com/idlix/IdlixProvider.kt",
    "JavGuru/src/main/kotlin/com/kraptor/JavGuru.kt",
    "Javtiful/src/main/kotlin/com/istarvin/Javtiful.kt",
    "KissKH/src/main/kotlin/com/byayzen/KissKH.kt",
    "Reelshort/src/main/kotlin/com/reelshort/Reelshort.kt",
    "Sulasok/src/main/kotlin/com/istarvin/Sulasok.kt",
    "TwitchProvider/src/main/kotlin/recloudstream/TwitchProvider.kt",
    "XSmoviebox/src/main/kotlin/com/XSmoviebox/XSmoviebox.kt",
    "Youperv/src/main/kotlin/com/byayzen/Youperv.kt",
    "YunshanID/src/main/kotlin/com/yunshanid/YunshanID.kt"
];

const basePath = 'D:/APK/mod/repos/CSNEW';

files.forEach(f => {
    const fullPath = path.join(basePath, f);
    if (!fs.existsSync(fullPath)) {
        console.log(`[NOT FOUND] ${f}`);
        return;
    }
    const content = fs.readFileSync(fullPath, 'utf8');
    const lines = content.split('\n');
    let loadLinksStart = -1;
    let braceCount = 0;
    const body = [];
    
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (loadLinksStart === -1 && line.includes('suspend fun loadLinks')) {
            loadLinksStart = i;
        }
        if (loadLinksStart !== -1) {
            body.push(`${i+1}: ${line}`);
            // brace counting
            const open = (line.match(/\{/g) || []).length;
            const close = (line.match(/\}/g) || []).length;
            braceCount += open - close;
            if (body.length > 5 && braceCount <= 0 && line.includes('}')) {
                break;
            }
        }
    }
    
    console.log(`\n=================== ${f} ===================`);
    console.log(body.join('\n'));
});
