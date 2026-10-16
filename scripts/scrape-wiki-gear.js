/**
 * OSRS Wiki Live Gear Scraper - Comprehensive Edition
 * Extracts strategy gear tables directly from Old School RuneScape Wiki MediaWiki API
 * across ALL bosses, raids, and slayer task assignments.
 * 
 * Sources:
 * 1. All strategy pages on the OSRS Wiki (intitle:Strategies)
 * 2. All pages embedding Template:Recommended_equipment
 * 3. All pages in Category:Slayer_task and Category:Strategies
 * 4. Equipment boxes (Template:Equipment) on creature pages
 * 5. Slayer training master setups for common slayer tasks
 * 
 * Maps item names using:
 * - Live Wiki tradeables mapping API
 * - RuneLite ItemID constants (16,893 items including untradeables and imbues)
 * - Curated alias map for sets, Barrows pieces, Blood Moon gear, and shields.
 */

const fs = require('fs');
const path = require('path');
const https = require('https');
const { execSync, execFileSync } = require('child_process');

const OUTPUT_RESOURCES = path.join(__dirname, '../src/main/resources/gear_database.json');
const OUTPUT_DATA = path.join(__dirname, '../data/gear_database.json');
const CACHE_DIR = path.join(__dirname, '../data/wiki_cache');
const CACHE_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 days local disk cache
const FORCE_FRESH = process.argv.includes('--fresh') || process.argv.includes('--force');

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// Comprehensive alias map for multi-item sets, interchangeable pieces, and degradations
const ALIAS_MAP = {
  // Shields & Protection
  'anti-dragon shield': [1540, 28731],
  'antifire shield': [1540, 28731],
  'dragonfire shield': [11283, 11284, 33186],
  'dragonfire ward': [22002, 22003],
  'twisted buckler': [21000],
  'avernic defender': [22322, 23630, 24749, 25075],
  'dragon defender': [12954, 19722, 20140],
  'rune defender': [8850],
  'mirror shield': [4156],
  "v's shield": [22308],

  // Barrows Interchangeable Sets (Clean and 100/75/50/25/0 degraded)
  'barrows platelegs': [4751, 4970, 4971, 4972, 4973, 4722, 4898, 4899, 4900, 4901, 4759, 4994, 4995, 4996, 4997, 4730, 4922, 4923, 4924, 4925, 4974, 4902, 4998, 4926],
  'barrows legs': [4751, 4970, 4971, 4972, 4973, 4722, 4898, 4899, 4900, 4901, 4759, 4994, 4995, 4996, 4997, 4730, 4922, 4923, 4924, 4925, 4974, 4902, 4998, 4926],
  'barrows platebody': [4749, 4964, 4965, 4966, 4967, 4720, 4892, 4893, 4894, 4895, 4728, 4916, 4917, 4918, 4919, 4757, 4988, 4989, 4990, 4991, 4968, 4896, 4920, 4992],
  'barrows body': [4749, 4964, 4965, 4966, 4967, 4720, 4892, 4893, 4894, 4895, 4728, 4916, 4917, 4918, 4919, 4757, 4988, 4989, 4990, 4991, 4968, 4896, 4920, 4992],
  'barrows helm': [4745, 4952, 4953, 4954, 4955, 4716, 4880, 4881, 4882, 4883, 4724, 4904, 4905, 4906, 4907, 4753, 4976, 4977, 4978, 4979, 4956, 4884, 4908, 4980],

  // Specific Barrows Pieces with Degraded Variations
  "torag's helm": [4745, 4952, 4953, 4954, 4955, 4956, 23637],
  "torag's platebody": [4749, 4964, 4965, 4966, 4967, 4968],
  "torag's platelegs": [4751, 4970, 4971, 4972, 4973, 4974, 23634],
  "dharok's helm": [4716, 4880, 4881, 4882, 4883, 4884, 23639],
  "dharok's platebody": [4720, 4892, 4893, 4894, 4895, 4896, 25515],
  "dharok's platelegs": [4722, 4898, 4899, 4900, 4901, 4902, 23633],
  "guthan's helm": [4724, 4904, 4905, 4906, 4907, 4908, 23638],
  "guthan's platebody": [4728, 4916, 4917, 4918, 4919, 4920],
  "guthan's chainskirt": [4730, 4922, 4923, 4924, 4925, 4926],
  "verac's helm": [4753, 4976, 4977, 4978, 4979, 4980, 23636],
  "verac's brassard": [4757, 4988, 4989, 4990, 4991, 4992, 27190],
  "verac's plateskirt": [4759, 4994, 4995, 4996, 4997, 4998, 23635],
  "ahrim's hood": [4708, 4856, 4857, 4858, 4859, 4860],
  "ahrim's robetop": [4712, 4868, 4869, 4870, 4871, 4872],
  "ahrim's robeskirt": [4714, 4874, 4875, 4876, 4877, 4878],
  "karil's coif": [4732, 4928, 4929, 4930, 4931, 4932],
  "karil's leathertop": [4736, 4940, 4941, 4942, 4943, 4944],
  "karil's leatherskirt": [4740, 4946, 4947, 4948, 4949, 4950],

  // Perilous Moons (Clean, Used, 0%, Broken)
  'blood moon helm': [29028, 29047, 29073, 29848],
  'blood moon chestplate': [29022, 29043, 29067, 29846],
  'blood moon tassets': [29025, 29045, 29070, 29847],
  'blue moon helm': [29034, 29053, 29079, 29845],
  'blue moon chestplate': [29031, 29051, 29076, 29840],
  'blue moon tassets': [29037, 29055, 29082, 29842],
  'eclipse moon helm': [29019, 29043, 29064, 29837],
  'eclipse moon chestplate': [29013, 29039, 29058, 29832],
  'eclipse moon tassets': [29016, 29041, 29061, 29834],

  // Slayer Helmets & Masks (all imbues and boss recolors)
  'slayer helmet (i)': [11865, 11864, 19641, 19645, 19649, 21266, 21888, 23075, 24444, 25179, 25916, 28076],
  'slayer helmet': [11864, 11865, 19639, 19643, 19647, 21264, 21886, 23073, 24442, 25177, 25914, 28074],
  'black mask (i)': [11784, 8921],
  'black mask': [8901, 11784],
  'nose peg': [4168],
  'facemask': [4164],
  'earmuffs': [4166],
  'spiny helmet': [4551],
  'witchwood icon': [8923],

  // Amulets & Jewelry
  'salve amulet(ei)': [12018],
  'salve amulet (ei)': [12018],
  'salve amulet (e)': [10588],
  'salve amulet': [4081],
  'amulet of rancour': [29801],
  'amulet of torture': [19553],
  'necklace of anguish': [19547],
  'occult necklace': [12002],
  'tormented bracelet': [19544],
  'ferocious gloves': [22981],
  'barrows gloves': [7462],

  // Rings (imbued / evolved)
  'ultor ring': [28307],
  'venator ring': [28310],
  'magus ring': [28313],
  'bellator ring': [28316],
  'berserker ring (i)': [11773],
  'archers ring (i)': [11771],
  'seers ring (i)': [11770],
  'warrior ring (i)': [11772],
  'ring of suffering (i)': [19550],
  'ring of suffering (ri)': [19550],
  'ring of shadows': [28325],
  'lightbearer': [25975],

  // Capes & Ammo
  'infernal cape': [21295],
  'fire cape': [6570],
  'mythical cape': [22114],
  "dizana's quiver": [28951, 28952, 28953],
  "ava's assembler": [22109],
  "ava's accumulator": [10499],
  'imbued saradomin cape': [21791],
  'imbued zamorak cape': [21795],
  'imbued guthix cape': [21793],
  'ardougne cloak 4': [13124],
  'ardougne cloak': [13124, 13123, 13122, 13121],
  "rada's blessing 4": [22947],
  "rada's blessing 3": [22945],
  'god blessing': [20220, 20223, 20226, 20229, 20232, 20235],
  'peaceful blessing': [20220],
  'holy blessing': [20223],
  'war blessing': [20226],
  'honourable blessing': [20229],
  'unholy blessing': [20232],

  // Boots
  'primordial boots': [13239],
  'pegasian boots': [13237],
  'eternal boots': [13235],
  'dragon boots': [11840],
  'ranger boots': [2577],
  'echo boots': [28805],
  'guardian boots': [21733],
  'granite boots': [21643],
  'mixed hide boots': [29286],
  'boots of stone': [22951],
  'boots of brimstone': [22954],
  'holy sandals': [12598],
  'devout boots': [22954],

  // Void Knight Equipment
  'void ranger helm': [11664],
  'void melee helm': [11665],
  'void mage helm': [11663],
  'elite void top': [13072],
  'elite void robe': [13073],
  'void knight top': [8839],
  'void knight robe': [8840],
  'void knight gloves': [8842],

  // Armor sets
  'torva full helm': [26382],
  'torva platebody': [26384],
  'torva platelegs': [26386],
  'bandos chestplate': [11832],
  'bandos tassets': [11834],
  'fighter torso': [10551],
  'neitiznot faceguard': [23230],
  'helm of neitiznot': [10828],
  'serpentine helm': [12931],
  'justiciar faceguard': [22326],
  'justiciar chestguard': [22327],
  'justiciar legguards': [22328],
  'oathplate helm': [29929],
  'oathplate chest': [29932],
  'oathplate legs': [29935],
  'masori mask (f)': [27226],
  'masori body (f)': [27229],
  'masori chaps (f)': [27232],
  'masori mask': [27351],
  'masori body': [27354],
  'masori chaps': [27357],
  'crystal helm': [23971],
  'crystal body': [23975],
  'crystal legs': [23979],
  'ancestral hat': [21018],
  'ancestral robe top': [21021],
  'ancestral robe bottom': [21024],
  'virtus mask': [26231],
  'virtus robe top': [26233],
  'virtus robe bottom': [26235],
  'proselyte hauberk': [9674],
  'proselyte cuisse': [9676],
  'proselyte tasset': [9678],
  'sunfire fanatic helm': [28807],
  'sunfire fanatic cuirass': [28809],
  'sunfire fanatic chausses': [28811],
  'shayzien platebody (5)': [13385],

  // Weapons & Specs
  'dragon hunter lance': [22978],
  'dragon hunter crossbow': [21902],
  'bow of faerdhinen': [25865],
  'bow of faerdhinen (c)': [25865],
  'toxic blowpipe': [12926],
  'twisted bow': [20997],
  'scythe of vitur': [22325, 22486],
  "tumeken's shadow": [27275],
  "osmumten's fang": [26219],
  'ghrazi rapier': [22324],
  'abyssal whip': [4151],
  'abyssal tentacle': [12006],
  'abyssal bludgeon': [13263],
  'zamorakian hasta': [11889],
  'voidwaker': [27690],
  'burning claws': [29577],
  'dragon claws': [13652],
  'dragon warhammer': [13576],
  'bandos godsword': [11804],
  'elder maul': [21003],
  'zaryte crossbow': [26374],
  'armadyl crossbow': [11785],
  'rune crossbow': [9185],
  'venator bow': [27610],
  'trident of the swamp': [12899],
  'trident of the seas': [11905],
  'sanguinesti staff': [22323],
  'kodai wand': [21006],
  'ancient sceptre': [27633],
  'nightmare staff': [24422],
  'blisterwood flail': [24691],
  'ivandis flail': [22398],
  'leaf-bladed battleaxe': [20727],
  'leaf-bladed sword': [11902],
  'leaf-bladed spear': [4158],
  'warped sceptre': [28585],
  'dwarf multicannon': [6, 8, 10, 12],

  // Slayer required / execution items & tools
  'rock hammer': [4162, 21742],
  'rock thrownhammer': [21742],
  'granite hammer': [21742],
  'bag of salt': [4161],
  'ice cooler': [6696],
  'fungicide spray': [7431, 7430, 7429, 7428, 7427, 7426, 7425, 7424, 7423, 7422, 7421],
  'fungicide spray 10': [7431],
  'fungicide': [7431],
  'brine sabre': [11037],
  'lit bug lantern': [7053],
  'bug lantern': [7053],
  'insulated boots': [7159],
  'spade': [952],
  'rope': [954],
  'hammer': [2347],
  'lockpick': [1523],
  'pet rock': [3695],
  'rune thrownaxe': [805],
  'light source': [20720, 4550, 4539, 4537, 4531, 4529, 5013],
  'bullseye lantern': [4550, 4548, 4546, 4544],
  'bruma torch': [20720],
  'kandarin headgear': [13137, 13138, 13139, 13140],
  'desert amulet 4': [13136],
  'desert amulet': [13136, 13135, 13134, 13133],
  'slayer ring': [11866],
  'dramen staff': [772],
  'lunar staff': [9084],
  'crystal pickaxe': [23680, 23682],
  'dragon pickaxe': [11920, 12797, 20786],
  'rune pickaxe': [1275],
  'pickaxe': [23680, 11920, 1275],
  'crystal axe': [23673],
  'dragon axe': [6739],
  'rune axe': [1359],
  'axe': [23673, 6739, 1359],

  // Utility, teleports, pouches & runes
  'rune pouch': [12791, 27281],
  'divine rune pouch': [27281],
  'book of the dead': [25818],
  'holy wrench': [6714],
  'bonecrusher': [13116],
  'bonecrusher necklace': [22986],
  'ash sanctifier': [25817],
  'bracelet of slaughter': [21183],
  'expeditious bracelet': [21177],
  'ring of dueling': [2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566],
  'ring of dueling(8)': [2552],
  'games necklace': [3853, 3855, 3857, 3859, 3861, 3863, 3865, 3867],
  'games necklace(8)': [3853],
  'teleport to house': [8013],
  'teleport to house (tablet)': [8013],
  'house teleport': [8013],
  'zul-andra teleport': [12938],
  'royal seed pod': [19564],
  'cannonball': [2],
  'cannonballs': [2],
  'death rune': [560],
  'blood rune': [565],
  'soul rune': [566],
  'wrath rune': [21880],
  'chaos rune': [562],
  'nature rune': [561],
  'law rune': [563],
  'dust rune': [4696],
  'fire rune': [554],
  'water rune': [555],
  'air rune': [556],
  'earth rune': [557],
  'diamond dragon bolts (e)': [21946],
  'ruby dragon bolts (e)': [21944],
  'diamond bolts (e)': [9243],
  'ruby bolts (e)': [9242],
  'dragon arrow': [11212],
  'amethyst arrow': [21326],
  'rune arrow': [892],
  'crimson kisten': [28801],
  'spectral spirit shield': [12821],

  // Common food
  'food': [391, 385, 3144, 13441, 7946, 379],
  'cheap food': [391, 385, 3144, 13441],
  'cooked karambwan': [3144],
  'karambwan': [3144],
  'karambwans': [3144],
  'manta ray': [391],
  'manta rays': [391],
  'anglerfish': [13441],
  'shark': [385],
  'sharks': [385],
  'monkfish': [7946],
  'lobster': [379],
  'cooked moonlight antelope': [29304],
  'moonlight antelope': [29304],
  'dark crab': [11936],
  'tuna potato': [7060],

  // Common prayer and restoration
  'cheap prayer': [2434, 3024],
  'prayer potion': [2434],
  'prayer potions': [2434],
  'super restore': [3024],
  'super restores': [3024],
  'sanfew serum': [10925],
  'sanfew serums': [10925],
  'prayer regeneration potion': [30125],

  // Combat boosters
  'super combat potion': [12695],
  'super combat potions': [12695],
  'divine super combat potion': [23685],
  'divine super combat potions': [23685],
  'ranging potion': [2444],
  'ranging potions': [2444],
  'divine ranging potion': [23733],
  'divine ranging potions': [23733],
  'bastion potion': [22461],
  'divine bastion potion': [24635],
  'magic potion': [3040],
  'forgotten brew': [28337],
  'saturated heart': [27641],
  'imbued heart': [20724],
  'heart': [27641, 20724],

  // Protection & Antidotes
  'antifire potion': [2452, 11951],
  'extended antifire': [11951],
  'super antifire potion': [21978, 22209],
  'super antifire': [21978],
  'extended super antifire': [22209],
  'anti-venom': [12905, 12913],
  'anti-venom+': [12913, 29824],
  'extended anti-venom+': [29824],
  'antipoison': [2446, 175],
  'superantipoison': [2448, 181],
  'stamina potion': [12625],
  'stamina potions': [12625],
  'saradomin brew': [6685],
  'saradomin brews': [6685],
  'goading potion': [29944],
  'goading potions': [29944]
};

function fetchJsonCached(url, cacheKey, ttlMs = CACHE_TTL_MS) {
  fs.mkdirSync(CACHE_DIR, { recursive: true });
  const cacheFile = path.join(CACHE_DIR, `${cacheKey}.json`);

  // 1. Fast local disk cache read
  if (!FORCE_FRESH && fs.existsSync(cacheFile)) {
    try {
      const stats = fs.statSync(cacheFile);
      const age = Date.now() - stats.mtimeMs;
      if (age < ttlMs) {
        const cached = JSON.parse(fs.readFileSync(cacheFile, 'utf8'));
        return Promise.resolve(cached);
      }
    } catch (e) {
      console.warn(`[Wiki Cache] Error reading cache file for ${cacheKey}, will refetch:`, e.message);
    }
  }

  // 2. Polite network fetch with compliant User-Agent and fallback
  return new Promise((resolve, reject) => {
    const options = {
      headers: {
        'User-Agent': 'RuneLiteGearOptimizer/1.0 (local-cache enabled; contact: jakepike2011@gmail.com)'
      }
    };

    https.get(url, options, res => {
      if (res.statusCode !== 200) {
        if (fs.existsSync(cacheFile)) {
          try {
            console.warn(`[Wiki Cache] HTTP ${res.statusCode} from ${url}. Serving local cache fallback.`);
            return resolve(JSON.parse(fs.readFileSync(cacheFile, 'utf8')));
          } catch (_) {}
        }
        return reject(new Error(`HTTP ${res.statusCode} fetching ${url}`));
      }

      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);
          try {
            fs.writeFileSync(cacheFile, JSON.stringify(parsed, null, 2), 'utf8');
          } catch (writeErr) {
            console.warn(`[Wiki Cache] Failed to write disk cache for ${cacheKey}:`, writeErr.message);
          }
          resolve(parsed);
        } catch (e) {
          reject(e);
        }
      });
    }).on('error', err => {
      if (fs.existsSync(cacheFile)) {
        try {
          console.warn(`[Wiki Cache] Network error (${err.message}). Serving local cache fallback.`);
          return resolve(JSON.parse(fs.readFileSync(cacheFile, 'utf8')));
        } catch (_) {}
      }
      reject(err);
    });
  });
}

function normalizeKey(str) {
  return str.toLowerCase().replace(/[^a-z0-9]/g, '');
}

function loadRuneLiteItemConstants() {
  const cacheDir = path.join(process.env.USERPROFILE || process.env.HOME || '', '.gradle/caches/modules-2/files-2.1/net.runelite/runelite-api');
  let javap = null;

  if (process.env.JAVA_HOME) {
    const candidate = path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'javap.exe' : 'javap');
    if (fs.existsSync(candidate)) javap = candidate;
  }
  if (!javap) {
    const defaultWin = 'C:\\Program Files\\ojdkbuild\\java-11-openjdk-11.0.15-1\\bin\\javap.exe';
    if (fs.existsSync(defaultWin)) javap = defaultWin;
  }

  const itemMap = new Map();

  if (!fs.existsSync(cacheDir) || !javap) {
    console.warn('[Wiki Scraper] Gradle cache or javap not found, using static alias map.');
    return itemMap;
  }

  try {
    let jarPath = null;
    const versions = fs.readdirSync(cacheDir);
    for (const ver of versions) {
      const verDir = path.join(cacheDir, ver);
      const hashes = fs.readdirSync(verDir);
      for (const h of hashes) {
        const hDir = path.join(verDir, h);
        for (const f of fs.readdirSync(hDir)) {
          if (f.endsWith('.jar') && !f.includes('sources') && !f.includes('javadoc')) {
            jarPath = path.join(hDir, f);
            break;
          }
        }
      }
    }

    if (!jarPath) return itemMap;

    console.log(`[Wiki Scraper] Extracting RuneLite ItemID constants from ${path.basename(jarPath)}...`);
    const out = execFileSync(javap, ['-constants', '-cp', jarPath, 'net.runelite.api.ItemID'], {
      maxBuffer: 40 * 1024 * 1024,
      encoding: 'utf8'
    });

    for (const line of out.split('\n')) {
      const m = line.match(/public static final int ([A-Za-z0-9_]+) = (-?[0-9]+);/);
      if (m) {
        const id = parseInt(m[2], 10);
        if (id > 0) {
          const norm = normalizeKey(m[1]);
          if (!itemMap.has(norm)) {
            itemMap.set(norm, id);
          }
        }
      }
    }

    console.log(`[Wiki Scraper] Successfully indexed ${itemMap.size} item constants from RuneLite.`);
  } catch (err) {
    console.warn('[Wiki Scraper] Failed to load RuneLite ItemID constants:', err.message);
  }

  return itemMap;
}

function extractTemplates(text, templateName) {
  const marker = '{{' + templateName;
  const lower = text.toLowerCase();
  let idx = lower.indexOf(marker.toLowerCase());
  const blocks = [];
  while (idx !== -1) {
    let braceCount = 0;
    let endIdx = -1;
    for (let i = idx; i < text.length - 1; i++) {
      if (text[i] === '{' && text[i+1] === '{') {
        braceCount++;
        i++;
      } else if (text[i] === '}' && text[i+1] === '}') {
        braceCount--;
        i++;
        if (braceCount === 0) {
          endIdx = i + 1;
          break;
        }
      }
    }
    if (braceCount === 0 && endIdx !== -1) {
      blocks.push(text.substring(idx + marker.length, endIdx - 2));
      idx = lower.indexOf(marker.toLowerCase(), endIdx);
    } else {
      // Unclosed or malformed template: advance past the marker to avoid infinite loops
      idx = lower.indexOf(marker.toLowerCase(), idx + marker.length);
    }
  }
  return blocks;
}

function resolveItemIds(rawName, tradeableMap, runeliteMap) {
  if (!rawName) return [];
  const clean = rawName.toLowerCase().replace(/\[\[|\]\]/g, '').trim();

  // 1. Check exact alias map
  if (ALIAS_MAP[clean]) {
    return ALIAS_MAP[clean];
  }

  // 2. Check tradeables mapping
  if (tradeableMap.has(clean)) {
    return [tradeableMap.get(clean)];
  }

  // 3. Fallback for potions without dose number specified e.g. "super combat potion" -> "super combat potion(4)"
  if (ALIAS_MAP[clean + '(4)']) {
    return ALIAS_MAP[clean + '(4)'];
  }
  if (tradeableMap.has(clean + '(4)')) {
    return [tradeableMap.get(clean + '(4)')];
  }
  if (tradeableMap.has(clean + ' (4)')) {
    return [tradeableMap.get(clean + ' (4)')];
  }

  // 4. Handle plural names (e.g. "prayer potions", "super restores", "sharks")
  if (clean.endsWith('s')) {
    const singular = clean.endsWith('es') ? clean.slice(0, -2) : clean.slice(0, -1);
    if (ALIAS_MAP[singular]) {
      return ALIAS_MAP[singular];
    }
    if (tradeableMap.has(singular)) {
      return [tradeableMap.get(singular)];
    }
    if (ALIAS_MAP[singular + '(4)']) {
      return ALIAS_MAP[singular + '(4)'];
    }
    if (tradeableMap.has(singular + '(4)')) {
      return [tradeableMap.get(singular + '(4)')];
    }
    const normSing = normalizeKey(singular);
    if (runeliteMap.has(normSing)) {
      return [runeliteMap.get(normSing)];
    }
  }

  // 5. Normalized alphanumeric lookup
  const norm = normalizeKey(clean);
  if (ALIAS_MAP[norm]) {
    return ALIAS_MAP[norm];
  }
  if (runeliteMap.has(norm)) {
    return [runeliteMap.get(norm)];
  }

  // 6. Handle stripped qualifiers e.g. (i), (e), (ei), (f), (c)
  const stripped = clean.replace(/\s*\([^)]*\)/g, '').trim();
  if (ALIAS_MAP[stripped]) {
    return ALIAS_MAP[stripped];
  }
  if (tradeableMap.has(stripped)) {
    return [tradeableMap.get(stripped)];
  }
  const normStripped = normalizeKey(stripped);
  if (runeliteMap.has(normStripped)) {
    return [runeliteMap.get(normStripped)];
  }

  return [];
}

function parseInventoryBlock(blockText, tradeableMap, runeliteMap) {
  const lines = blockText.split('\n');
  const items = [];
  let meleeScore = 0;
  let rangedScore = 0;
  let magicScore = 0;

  for (const line of lines) {
    const match = line.match(/^\|\s*(\d+|rune\d*)\s*=\s*(.*)$/i);
    if (!match) continue;

    let val = match[2].trim();
    if (!val || /^(n\/a|empty|none|-)$/i.test(val)) continue;

    // Quantity extraction: e.g. "Death rune\5000" or "Rune arrow / 500" or "Ruby dragon bolts (e) \ 50"
    let qty = 1;
    const slashMatch = val.match(/^([^\/\\]+?)\s*[\\/]\s*(\d+)$/);
    if (slashMatch) {
      val = slashMatch[1].trim();
      qty = parseInt(slashMatch[2], 10) || 1;
    }

    // Number prefix extraction: e.g. "2x Super combat potion(4)" or "6 Prayer potion(4)"
    const numPrefixMatch = val.match(/^(\d+)x?\s+(.*)$/i);
    if (numPrefixMatch && !/^\d+\s*=\s*/.test(val)) {
      qty = parseInt(numPrefixMatch[1], 10) || 1;
      val = numPrefixMatch[2].trim();
    }

    // Handle template shortcuts inside inventory slots
    if (/\{\{cheap prayer\}\}/i.test(val)) {
      val = 'Prayer potion(4)';
    } else if (/\{\{cheap food\}\}/i.test(val)) {
      val = 'Manta ray';
    } else {
      const minPriceMatch = val.match(/\{\{MinPrice[^|]*\|(?:format=item\|)?([^|}]+)/i);
      if (minPriceMatch) {
        val = minPriceMatch[1].trim();
      }
      const plinkMatch = val.match(/\{\{plink[a-z]*\|([^|}]+)/i);
      if (plinkMatch) {
        val = plinkMatch[1].trim();
      }
    }

    val = val.replace(/<!--.*?-->/g, '').replace(/\[\[([^|\]]+)(?:\|[^\]]+)?\]\]/g, '$1').replace(/[{}[\]]/g, '').trim();
    if (!val || /^(n\/a|empty|none|-)$/i.test(val)) continue;

    // Combat style scoring
    const valLower = val.toLowerCase();
    if (/ranging|bastion|crossbow|blowpipe|chinchompa|twisted bow|quiver|assembler|accumulator|bolts|arrow|masori/i.test(valLower)) {
      rangedScore++;
    } else if (/super combat|combat potion|strength pot|attack pot|godsword|claws|fang|scythe|voidwaker|whip|bludgeon|defender|torva|bandos/i.test(valLower)) {
      meleeScore++;
    } else if (/magic potion|forgotten brew|saturated heart|imbued heart|shadow|trident|sang|wand|sceptre|ancestral|virtus|ward|rune pouch/i.test(valLower)) {
      magicScore++;
    }

    const ids = resolveItemIds(val, tradeableMap, runeliteMap);
    if (ids && ids.length > 0) {
      items.push({ id: ids[0], qty: qty, optional: false });
    }
  }

  let detectedStyle = 'Melee';
  if (rangedScore > meleeScore && rangedScore > magicScore) {
    detectedStyle = 'Ranged';
  } else if (magicScore > meleeScore && magicScore > rangedScore) {
    detectedStyle = 'Magic';
  }

  return { items, detectedStyle };
}

function extractBulletInventory(wikitext, tradeableMap, runeliteMap) {
  const items = [];
  const lines = wikitext.split('\n');
  let inInvSection = false;

  for (const line of lines) {
    if (/^==+[^=]*(inventory|supplies|equipment).*==+/i.test(line)) {
      inInvSection = true;
      continue;
    } else if (inInvSection && /^==+[^=]+==+/.test(line)) {
      inInvSection = false;
    }

    if (!line.startsWith('*')) continue;

    const lineText = line.replace(/^\*+\s*/, '').trim();

    // Look for link pattern: [[ItemName]] or [[ItemName|Display]]
    const linkMatches = [...lineText.matchAll(/\[\[([^|\]]+)(?:\|[^\]]+)?\]\]/g)];
    for (const lm of linkMatches) {
      const candidate = lm[1].trim();
      const ids = resolveItemIds(candidate, tradeableMap, runeliteMap);
      if (ids && ids.length > 0) {
        // Extract quantity if preceded by number, e.g. "2 [[super combat potion]]"
        let qty = 1;
        const prefix = lineText.substring(0, lm.index).trim();
        const numMatch = prefix.match(/(\d+)\s*x?$/);
        if (numMatch) {
          qty = parseInt(numMatch[1], 10) || 1;
        }
        if (!items.some(it => it.id === ids[0])) {
          items.push({ id: ids[0], qty: qty, optional: false });
        }
      }
    }
  }

  return items;
}

function chooseInventoryForActivity(parsedInvBlocks, bulletInv, combatStyle, index, baseName) {
  let invItems = [];

  // 1. Try to find an inventory block matching combatStyle
  const matched = parsedInvBlocks.filter(b => b.detectedStyle.toLowerCase() === combatStyle.toLowerCase());
  if (matched.length > 0) {
    invItems = JSON.parse(JSON.stringify(matched[Math.min(index, matched.length - 1)].items));
  } else if (parsedInvBlocks.length === 1) {
    invItems = JSON.parse(JSON.stringify(parsedInvBlocks[0].items));
  } else if (parsedInvBlocks.length > 0) {
    invItems = JSON.parse(JSON.stringify(parsedInvBlocks[Math.min(index, parsedInvBlocks.length - 1)].items));
  } else if (bulletInv.length > 0) {
    invItems = JSON.parse(JSON.stringify(bulletInv));
  }

  // If still empty or minimal, supply standard realistic defaults based on combat style
  if (invItems.length === 0) {
    if (combatStyle === 'Ranged') {
      invItems = [
        { id: 23733, qty: 1, optional: false }, // Divine ranging potion(4)
        { id: 2434, qty: 4, optional: false },  // Prayer potion(4)
        { id: 3144, qty: 8, optional: false },  // Cooked karambwan
        { id: 8013, qty: 1, optional: false }   // Teleport to house
      ];
    } else if (combatStyle === 'Magic') {
      invItems = [
        { id: 2434, qty: 5, optional: false },  // Prayer potion(4)
        { id: 3144, qty: 6, optional: false },  // Cooked karambwan
        { id: 27281, qty: 1, optional: false }, // Divine rune pouch
        { id: 8013, qty: 1, optional: false }   // Teleport to house
      ];
    } else {
      invItems = [
        { id: 23685, qty: 1, optional: false }, // Divine super combat potion(4)
        { id: 2434, qty: 4, optional: false },  // Prayer potion(4)
        { id: 3144, qty: 8, optional: false },  // Cooked karambwan
        { id: 8013, qty: 1, optional: false }   // Teleport to house
      ];
    }
  }

  // Ensure mandatory task & boss supplies/tools are present
  const lowerBase = baseName.toLowerCase();
  function ensureItem(itemId, qty = 1) {
    if (!invItems.some(it => it.id === itemId)) {
      invItems.unshift({ id: itemId, qty: qty, optional: false });
    }
  }

  if (lowerBase.includes('vorkath')) {
    ensureItem(4170, 1);  // Slayer's staff (for Crumble Undead)
    ensureItem(22209, 1); // Extended super antifire(4)
    ensureItem(29824, 1); // Extended anti-venom+(4)
    ensureItem(12791, 1); // Rune pouch
  } else if (lowerBase.includes('duke sucellus')) {
    ensureItem(23680, 1); // Crystal pickaxe
    ensureItem(20720, 1); // Bruma torch (light source)
  } else if (lowerBase.includes('gargoyle')) {
    ensureItem(4162, 1);  // Rock hammer
  } else if (lowerBase.includes('lizard') && !lowerBase.includes('lizardman')) {
    ensureItem(6696, 20); // Ice cooler
  } else if (lowerBase.includes('zygomite')) {
    ensureItem(7431, 1);  // Fungicide spray 10
  } else if (lowerBase.includes('rockslug') || lowerBase.includes('rock slug')) {
    ensureItem(4161, 10); // Bag of salt
  } else if (lowerBase.includes('barrows')) {
    ensureItem(952, 1);   // Spade
  } else if (lowerBase.includes('giant mole')) {
    ensureItem(952, 1);   // Spade
    ensureItem(20720, 1); // Light source
  } else if (lowerBase.includes('kalphite queen')) {
    ensureItem(954, 2);   // Rope
  } else if (lowerBase.includes('dagannoth king')) {
    ensureItem(3695, 1);  // Pet rock
    ensureItem(805, 1);   // Rune thrownaxe
  } else if (lowerBase.includes('cave horror')) {
    ensureItem(20720, 1); // Light source
  } else if (lowerBase.includes('cerberus')) {
    if (combatStyle === 'Ranged') {
      ensureItem(12821, 1); // Spectral spirit shield
    }
  } else if (lowerBase.includes('alchemical hydra')) {
    ensureItem(30125, 1); // Prayer regeneration potion(4)
  } else if (lowerBase.includes('zulrah')) {
    ensureItem(29824, 1); // Extended anti-venom+(4)
    ensureItem(2552, 1);  // Ring of dueling
  }

  return invItems;
}

function parseSlotItems(lineValue, tradeableMap, runeliteMap) {
  const ids = [];
  const tokens = lineValue.split(/\/|<br\s*\/?>|<\/br>|,|>/i);

  for (const token of tokens) {
    let itemToken = token.replace(/\{\{efn[^}]*\}\}/gi, '').trim();

    // Check for {{plink|Item|pic=RealItem}} or {{plinkp|Item}}
    const plinkMatch = itemToken.match(/\{\{plink[a-z]*\|([^|}]+)(?:\|pic=([^|}]+))?(?:\|txt=([^|}]+))?[^}]*\}\}/i);
    if (plinkMatch) {
      if (plinkMatch[1]) {
        ids.push(...resolveItemIds(plinkMatch[1], tradeableMap, runeliteMap));
      }
      if (plinkMatch[2]) {
        ids.push(...resolveItemIds(plinkMatch[2], tradeableMap, runeliteMap));
      }
      continue;
    }

    // Check for [[Item]]
    const wikiLinkMatch = itemToken.match(/\[\[([^|\]]+)(?:\|[^\]]+)?\]\]/);
    if (wikiLinkMatch) {
      ids.push(...resolveItemIds(wikiLinkMatch[1], tradeableMap, runeliteMap));
      continue;
    }

    // Check raw name
    const rawClean = itemToken.replace(/[{}[\]]/g, '').trim();
    if (rawClean.length > 1) {
      ids.push(...resolveItemIds(rawClean, tradeableMap, runeliteMap));
    }
  }

  return [...new Set(ids)];
}

function parseEquipmentBox(blockText, tradeableMap, runeliteMap) {
  const lines = blockText.split('\n');
  const slots = {};
  let style = 'Melee';

  const slotMap = {
    head: 'head',
    helm: 'head',
    neck: 'neck',
    cape: 'cape',
    ammo: 'ammo',
    weapon: 'weapon',
    torso: 'body',
    body: 'body',
    chest: 'body',
    legs: 'legs',
    legs2: 'legs',
    shield: 'shield',
    offhand: 'shield',
    gloves: 'hands',
    hands: 'hands',
    boots: 'boots',
    feet: 'boots',
    ring: 'ring',
    special: 'special'
  };

  for (const line of lines) {
    const match = line.match(/^\|\s*([a-zA-Z0-9_]+)\s*=\s*(.*)$/);
    if (!match) continue;

    const param = match[1].toLowerCase().trim();
    const val = match[2].trim();
    if (!val || val === 'N/A') continue;

    if (slotMap[param]) {
      const slotName = slotMap[param];
      const ids = parseSlotItems(val, tradeableMap, runeliteMap);
      if (ids.length > 0) {
        slots[slotName] = ids;
        if (slotName === 'weapon') {
          const valLower = val.toLowerCase();
          if (/bow|blowpipe|chinchompa|crossbow|atlatl/i.test(valLower)) {
            style = 'Ranged';
          } else if (/staff|wand|trident|sceptre|shadow/i.test(valLower)) {
            style = 'Magic';
          }
        }
      }
    }
  }

  return { slots, style };
}

async function scrapeWikiGear() {
  console.log('[Wiki Scraper] Starting comprehensive OSRS Wiki gear synchronization...');

  // 1. Load item mappings (using local disk cache when available)
  console.log('[Wiki Scraper] Loading tradeable mappings (local cache enabled)...');
  const tradeablesJson = await fetchJsonCached('https://prices.runescape.wiki/api/v1/osrs/mapping', 'osrs_tradeables_mapping');
  const tradeableMap = new Map();
  for (const item of tradeablesJson) {
    tradeableMap.set(item.name.toLowerCase().trim(), item.id);
  }
  console.log(`[Wiki Scraper] Loaded ${tradeableMap.size} tradeable items.`);

  const runeliteMap = loadRuneLiteItemConstants();

  // 2. Discover ALL candidate strategy and slayer pages (using local disk cache)
  console.log('[Wiki Scraper] Discovering strategy & slayer pages (local cache enabled)...');
  const [strategies, slayerCategory, embeddedRec] = await Promise.all([
    fetchJsonCached('https://oldschool.runescape.wiki/api.php?action=query&list=search&srsearch=intitle:Strategies&srlimit=500&format=json', 'query_strategies'),
    fetchJsonCached('https://oldschool.runescape.wiki/api.php?action=query&list=categorymembers&cmtitle=Category:Slayer_task&cmlimit=500&format=json', 'query_slayer_category'),
    fetchJsonCached('https://oldschool.runescape.wiki/api.php?action=query&list=embeddedin&eititle=Template:Recommended_equipment&eilimit=500&format=json', 'query_recommended_equipment')
  ]);

  const allPages = new Set();
  strategies.query.search.forEach(s => allPages.add(s.title));
  slayerCategory.query.categorymembers.forEach(s => allPages.add(s.title));
  embeddedRec.query.embeddedin.forEach(s => allPages.add(s.title));

  // Explicitly ensure core master pages are included
  allPages.add('Slayer training');
  allPages.add('Theatre of Blood/Hard Mode');
  allPages.add('Chambers of Xeric/Challenge Mode');

  const ignoredPrefixes = ['Template:', 'User:', 'Module:', 'User talk:', 'Guide:Forestry'];
  const validPages = Array.from(allPages).filter(title => {
    return !ignoredPrefixes.some(p => title.startsWith(p));
  });

  console.log(`[Wiki Scraper] Found ${validPages.length} total strategy candidates to process.`);

  const activities = {};

  // 3. Batch fetch wikitext (40 pages per request, cached locally, rate-limited)
  const totalBatches = Math.ceil(validPages.length / 40);
  for (let i = 0; i < validPages.length; i += 40) {
    const chunk = validPages.slice(i, i + 40);
    const batchNum = Math.floor(i / 40) + 1;
    const batchCacheKey = `wikitext_batch_${batchNum}`;
    const titlesParam = encodeURIComponent(chunk.join('|'));
    const batchUrl = `https://oldschool.runescape.wiki/api.php?action=query&prop=revisions&rvprop=content&rvslots=main&titles=${titlesParam}&format=json`;

    try {
      const cachePath = path.join(CACHE_DIR, `${batchCacheKey}.json`);
      const isCached = !FORCE_FRESH && fs.existsSync(cachePath);
      if (!isCached && i > 0) {
        await sleep(250); // Polite rate limit between live network calls
      }
      console.log(`[Wiki Scraper] Batch ${batchNum}/${totalBatches}: Processing ${chunk.length} pages ${isCached ? '(from local disk cache)' : '(fetching politely)'}...`);
      const batchRes = await fetchJsonCached(batchUrl, batchCacheKey);
      const pages = batchRes.query ? batchRes.query.pages : {};

      for (const pid of Object.keys(pages)) {
        const page = pages[pid];
        if (!page.revisions || !page.revisions[0]) continue;

        const title = page.title;
        const wikitext = page.revisions[0].slots.main['*'];

        // Determine base activity name and category
        let baseName = title;
        let category = 'Bossing';

        if (title.startsWith('Slayer task/')) {
          baseName = title.substring('Slayer task/'.length);
          category = 'Slayer';
        } else if (title.endsWith('/Strategies')) {
          baseName = title.substring(0, title.length - '/Strategies'.length);
          if (/chambers|amascut|theatre of blood/i.test(baseName)) {
            category = 'Raids';
          } else {
            category = 'Bossing';
          }
        } else if (title === 'Slayer training') {
          baseName = 'Slayer Training';
          category = 'Slayer';
        }

        baseName = baseName.replace(/_/g, ' ').trim();

        // Extract all Inventory and Loadout blocks on the page
        const invTemplates = extractTemplates(wikitext, 'Inventory');
        const loadoutTemplates = extractTemplates(wikitext, 'Loadout');
        const allInvBlocks = [...invTemplates, ...loadoutTemplates];
        const parsedInvBlocks = allInvBlocks.map(b => parseInventoryBlock(b, tradeableMap, runeliteMap)).filter(p => p.items.length > 0);
        const bulletInv = extractBulletInventory(wikitext, tradeableMap, runeliteMap);

        // Check for Recommended equipment templates
        const gearBlocks = extractTemplates(wikitext, 'Recommended equipment');
        for (let b = 0; b < gearBlocks.length; b++) {
          const block = gearBlocks[b];
          const lines = block.split('\n');

          let rawStyleVal = 'Melee';
          let combatStyle = 'Melee';
          const slots = {};

          for (const line of lines) {
            const match = line.match(/^\|\s*([a-zA-Z0-9_]+)\s*=\s*(.*)$/);
            if (!match) continue;

            const param = match[1].toLowerCase().trim();
            const val = match[2].trim();

            if (param === 'style') {
              rawStyleVal = val;
              const valLower = val.toLowerCase();
              if (/range|bow|chin/i.test(valLower)) {
                combatStyle = 'Ranged';
              } else if (/magic|mage|burst|barrag/i.test(valLower)) {
                combatStyle = 'Magic';
              } else {
                combatStyle = 'Melee';
              }
              continue;
            }

            const slotMatch = param.match(/^([a-zA-Z]+)(\d*)$/);
            if (!slotMatch) continue;

            let slotName = slotMatch[1];
            if (slotName === 'feet') slotName = 'boots';

            const slotIds = parseSlotItems(val, tradeableMap, runeliteMap);
            if (slotIds.length > 0) {
              if (!slots[slotName]) slots[slotName] = [];
              for (const id of slotIds) {
                if (!slots[slotName].includes(id)) {
                  slots[slotName].push(id);
                }
              }
            }
          }

          if (Object.keys(slots).length >= 3) {
            let variantLabel = rawStyleVal.replace(/[{}[\]]/g, '').trim();
            if (!variantLabel || variantLabel.toLowerCase() === combatStyle.toLowerCase()) {
              variantLabel = combatStyle;
            }

            const safeBase = baseName.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
            const safeVariant = variantLabel.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
            let activityId = `${safeBase}_${safeVariant}`;

            if (category === 'Slayer' && !activityId.startsWith('slayer_')) {
              activityId = `slayer_${activityId}`;
            }

            if (activities[activityId]) {
              activityId = `${activityId}_${b + 1}`;
            }

            const activityName = `${baseName} (${variantLabel})`;

            // Ensure Vorkath essential gear is complete
            if (baseName.toLowerCase().includes('vorkath')) {
              if (combatStyle === 'Melee') {
                if (!slots.shield) slots.shield = [];
                if (!slots.shield.includes(11283)) slots.shield.push(11283, 11284);
                if (!slots.shield.includes(1540)) slots.shield.push(1540);
                if (!slots.head) slots.head = [];
                if (!slots.head.includes(29028)) slots.head.push(29028, 29047);
                if (!slots.body) slots.body = [];
                if (!slots.body.includes(29022)) slots.body.push(29022, 29043);
                if (!slots.legs) slots.legs = [];
                if (!slots.legs.includes(29025)) slots.legs.push(29025, 29045);
              } else if (combatStyle === 'Ranged') {
                if (!slots.shield) slots.shield = [];
                if (!slots.shield.includes(22002)) slots.shield.push(22002, 22003);
                if (!slots.shield.includes(1540)) slots.shield.push(1540);
                if (!slots.shield.includes(11283)) slots.shield.push(11283, 11284);
              }
            }

            const assignedInventory = chooseInventoryForActivity(parsedInvBlocks, bulletInv, combatStyle, b, baseName);

            activities[activityId] = {
              id: activityId,
              name: activityName,
              category: category,
              style: combatStyle,
              slots: slots,
              inventory: assignedInventory
            };
          }
        }

        // If no Recommended equipment, check for Template:Equipment boxes (e.g. Cave horrors, Gryphons, etc.)
        if (gearBlocks.length === 0) {
          const eqBoxes = extractTemplates(wikitext, 'Equipment');
          for (let eb = 0; eb < eqBoxes.length; eb++) {
            const parsed = parseEquipmentBox(eqBoxes[eb], tradeableMap, runeliteMap);
            if (Object.keys(parsed.slots).length >= 5) {
              const safeBase = baseName.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
              let activityId = `${safeBase}_${parsed.style.toLowerCase()}`;
              if (category === 'Slayer' && !activityId.startsWith('slayer_')) {
                activityId = `slayer_${activityId}`;
              }
              if (activities[activityId]) {
                activityId = `${activityId}_${eb + 1}`;
              }

              const activityName = `${baseName} (${parsed.style})`;
              const assignedInventory = chooseInventoryForActivity(parsedInvBlocks, bulletInv, parsed.style, eb, baseName);
              activities[activityId] = {
                id: activityId,
                name: activityName,
                category: category,
                style: parsed.style,
                slots: parsed.slots,
                inventory: assignedInventory
              };
            }
          }
        }
      }
    } catch (batchErr) {
      console.warn(`[Wiki Scraper] Batch fetch error around index ${i}:`, batchErr.message);
    }
  }

  // 4. Enrich remaining standard Slayer task assignments with master Slayer setups
  // If a task in Category:Slayer_task was assigned but had no standalone table (e.g. Bloodveld, Hellhounds, Fire giants),
  // equip with optimal Slayer task profiles from OSRS Slayer guides
  const masterSlayerMelee = activities['slayer_training_melee'] ? activities['slayer_training_melee'].slots : {
    head: [11865, 11864, 26382, 11832, 23230, 29028, 10828],
    neck: [29801, 19553, 6585],
    cape: [21295, 6570, 13124],
    body: [26384, 11832, 29022, 10551],
    legs: [26386, 11834, 29025, 4751, 4722],
    weapon: [22324, 26219, 4151, 12006],
    shield: [22322, 12954, 8850],
    ammo: [22947, 20220],
    hands: [22981, 7462],
    boots: [13239, 11840],
    ring: [28307, 11773, 25975]
  };

  const masterSlayerPrayer = {
    head: [11865, 11864, 28807, 9674],
    neck: [22111, 19553, 6585],
    cape: [13124, 21295, 6570],
    body: [28809, 9674, 11832, 29022],
    legs: [28811, 9676, 11834, 29025],
    weapon: [22324, 26219, 4151],
    shield: [22322, 12954],
    ammo: [22947, 20220],
    hands: [22981, 7462],
    boots: [22954, 12598, 13239],
    ring: [28307, 11773, 25975]
  };

  const masterSlayerBarrage = activities['slayer_training_magic_barrage'] ? activities['slayer_training_magic_barrage'].slots : {
    head: [11865, 11864, 21018, 26231, 4708],
    neck: [12002, 19553],
    cape: [21791, 21795, 21793],
    body: [21021, 26233, 4712],
    legs: [21024, 26235, 4714],
    weapon: [21006, 27633, 24422],
    shield: [27253, 27251, 12899],
    ammo: [22947, 20220],
    hands: [19544, 7462],
    boots: [13235, 6920],
    ring: [28313, 25975, 11770]
  };

  const masterSlayerCannon = activities['slayer_training_ranged'] ? activities['slayer_training_ranged'].slots : {
    head: [11865, 11864, 27226, 27351],
    neck: [19547, 6585],
    cape: [28951, 22109, 10499],
    body: [27229, 27354, 23975, 2503],
    legs: [27232, 27357, 23979, 2497],
    weapon: [27610, 12926, 21902],
    ammo: [22947, 20220],
    hands: [26235, 7462],
    boots: [13237, 2577],
    ring: [28310, 11771, 25975]
  };

  // Specific task assignment mappings for general Slayer tasks
  const tasksToEnrich = [
    { name: 'Bloodveld', prayer: true, cannon: true },
    { name: 'Hellhounds', prayer: true, cannon: true },
    { name: 'Black demons', prayer: true, cannon: true },
    { name: 'Greater demons', prayer: true, cannon: true },
    { name: 'Fire giants', prayer: true, cannon: true },
    { name: 'Aberrant spectres', prayer: true, cannon: false, reqHead: 4168 },
    { name: 'Smoke devils', barrage: true },
    { name: 'Araxytes', prayer: true },
    { name: 'Kalphites', cannon: true },
    { name: 'Basilisks', reqShield: 4156 },
    { name: 'Ankou', barrage: true, prayer: true },
    { name: 'Dagannoth', cannon: true },
    { name: 'Lizardmen', reqBody: 13385, cannon: true },
    { name: 'Gargoyles', reqItem: 4162 },
    { name: 'Desert lizards', reqItem: 6696, reqItemQty: 20 },
    { name: 'Mutated zygomites', reqItem: 7431 },
    { name: 'Rockslugs', reqItem: 4161, reqItemQty: 10 },
    { name: 'Cave horrors', reqItem: 20720 },
    { name: 'Turoth', reqWeapon: [20727, 11902, 4158] },
    { name: 'Vampyres', reqWeapon: [24691, 22398] },
    { name: 'Warped creatures', reqWeapon: [28585] }
  ];

  for (const t of tasksToEnrich) {
    const safeBase = t.name.toLowerCase().replace(/[^a-z0-9]+/g, '_');

    // 1. Melee / Prayer variant
    const meleeId = `slayer_${safeBase}_melee`;
    if (!activities[meleeId]) {
      const slots = JSON.parse(JSON.stringify(t.prayer ? masterSlayerPrayer : masterSlayerMelee));
      if (t.reqHead) slots.head = [11865, 11864, t.reqHead];
      if (t.reqShield) slots.shield = [t.reqShield, ...slots.shield];
      if (t.reqWeapon) slots.weapon = [...t.reqWeapon, ...slots.weapon];
      if (t.reqBody) slots.body = [t.reqBody, ...slots.body];

      const meleeInv = [
        { id: 23685, qty: 1, optional: false }, // Divine super combat potion(4)
        { id: 2434, qty: t.prayer ? 6 : 3, optional: false }, // Prayer potion(4)
        { id: 3144, qty: 8, optional: false },  // Cooked karambwan
        { id: 12791, qty: 1, optional: false }, // Rune pouch
        { id: 8013, qty: 1, optional: false }   // Teleport to house
      ];
      if (t.reqItem) {
        meleeInv.unshift({ id: t.reqItem, qty: t.reqItemQty || 1, optional: false });
      }

      activities[meleeId] = {
        id: meleeId,
        name: `${t.name} (Melee${t.prayer ? ' - Prayer' : ''})`,
        category: 'Slayer',
        style: 'Melee',
        slots: slots,
        inventory: meleeInv
      };
    }

    // 2. Barrage variant
    if (t.barrage) {
      const barrageId = `slayer_${safeBase}_magic_barrage`;
      if (!activities[barrageId]) {
        activities[barrageId] = {
          id: barrageId,
          name: `${t.name} (Magic - Barrage)`,
          category: 'Slayer',
          style: 'Magic',
          slots: JSON.parse(JSON.stringify(masterSlayerBarrage)),
          inventory: [
            { id: 2434, qty: 6, optional: false },   // Prayer potion(4)
            { id: 3144, qty: 4, optional: false },   // Cooked karambwan
            { id: 27281, qty: 1, optional: false },  // Divine rune pouch
            { id: 560, qty: 1000, optional: false }, // Death rune
            { id: 565, qty: 1000, optional: false }, // Blood rune
            { id: 555, qty: 4000, optional: false }, // Water rune
            { id: 8013, qty: 1, optional: false }    // Teleport to house
          ]
        };
      }
    }

    // 3. Cannon / Ranged variant
    if (t.cannon) {
      const rangedId = `slayer_${safeBase}_ranged_cannon`;
      if (!activities[rangedId]) {
        activities[rangedId] = {
          id: rangedId,
          name: `${t.name} (Ranged - Cannon)`,
          category: 'Slayer',
          style: 'Ranged',
          slots: JSON.parse(JSON.stringify(masterSlayerCannon)),
          inventory: [
            { id: 23733, qty: 1, optional: false }, // Divine ranging potion(4)
            { id: 2434, qty: 4, optional: false },  // Prayer potion(4)
            { id: 3144, qty: 6, optional: false },  // Cooked karambwan
            { id: 2, qty: 1500, optional: false },  // Cannonball
            { id: 6, qty: 1, optional: false },     // Cannon base
            { id: 8, qty: 1, optional: false },     // Cannon stand
            { id: 10, qty: 1, optional: false },    // Cannon barrels
            { id: 12, qty: 1, optional: false },    // Cannon furnace
            { id: 8013, qty: 1, optional: false }   // Teleport to house
          ]
        };
      }
    }
  }

  const activityCount = Object.keys(activities).length;
  console.log(`[Wiki Scraper] Synchronization complete! Extracted ${activityCount} total setups across Bossing, Slayer, and Raids.`);

  const finalDb = {
    version: parseInt(new Date().toISOString().slice(0, 10).replace(/-/g, ''), 10),
    last_updated: new Date().toISOString(),
    activities: activities
  };

  const jsonStr = JSON.stringify(finalDb, null, 2);

  fs.mkdirSync(path.dirname(OUTPUT_RESOURCES), { recursive: true });
  fs.writeFileSync(OUTPUT_RESOURCES, jsonStr);

  fs.mkdirSync(path.dirname(OUTPUT_DATA), { recursive: true });
  fs.writeFileSync(OUTPUT_DATA, jsonStr);

  console.log(`[Wiki Scraper] Successfully saved ${activityCount} activities to ${OUTPUT_RESOURCES} and ${OUTPUT_DATA}!`);
}

scrapeWikiGear().catch(err => {
  console.error('[Wiki Scraper] Fatal error:', err);
  process.exit(1);
});