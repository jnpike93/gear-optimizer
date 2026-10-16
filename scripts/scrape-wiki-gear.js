/**
 * OSRS Wiki Live Gear Scraper
 * Extracts strategy gear tables directly from Old School RuneScape Wiki MediaWiki API
 * across ALL bosses, raids, and slayer task assignments.
 * 
 * Maps item names and aliases using:
 * 1. Live Wiki Price & Mapping API (tradeables)
 * 2. RuneLite ItemID constants via javap (untradeables, imbuements, degradations)
 * 3. Curated alias map for sets, Barrows pieces, Blood Moon gear, and shields.
 */

const fs = require('fs');
const path = require('path');
const https = require('https');
const { execSync } = require('child_process');

const OUTPUT_RESOURCES = path.join(__dirname, '../src/main/resources/gear_database.json');
const OUTPUT_DATA = path.join(__dirname, '../data/gear_database.json');

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
  'trident of the swamp': [12899],
  'trident of the seas': [11905],
  'sanguinesti staff': [22323]
};

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': 'RuneLiteGearOptimizer/1.0' } }, res => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          reject(e);
        }
      });
    }).on('error', reject);
  });
}

function normalizeKey(str) {
  return str.toLowerCase().replace(/[^a-z0-9]/g, '');
}

function loadRuneLiteItemConstants() {
  const cacheDir = path.join(process.env.USERPROFILE, '.gradle/caches/modules-2/files-2.1/net.runelite/runelite-api');
  const javap = 'C:\\Program Files\\ojdkbuild\\java-11-openjdk-11.0.15-1\\bin\\javap.exe';
  const itemMap = new Map();

  if (!fs.existsSync(cacheDir) || !fs.existsSync(javap)) {
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
    const out = execSync(`"${javap}" -constants -cp "${jarPath}" net.runelite.api.ItemID`, {
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
    let endIdx = idx;
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
    if (braceCount === 0) {
      blocks.push(text.substring(idx + marker.length, endIdx - 2));
    }
    idx = lower.indexOf(marker.toLowerCase(), endIdx);
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

  // 3. Normalized alphanumeric lookup
  const norm = normalizeKey(clean);
  if (ALIAS_MAP[norm]) {
    return ALIAS_MAP[norm];
  }
  if (runeliteMap.has(norm)) {
    return [runeliteMap.get(norm)];
  }

  // 4. Handle stripped qualifiers e.g. (i), (e), (ei), (f), (c)
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

async function scrapeWikiGear() {
  console.log('[Wiki Scraper] Starting full wiki synchronization for EVERY Boss and Slayer task...');

  // 1. Load item mappings
  console.log('[Wiki Scraper] Fetching live tradeable mappings from OSRS Wiki API...');
  const tradeablesJson = await fetchJson('https://prices.runescape.wiki/api/v1/osrs/mapping');
  const tradeableMap = new Map();
  for (const item of tradeablesJson) {
    tradeableMap.set(item.name.toLowerCase().trim(), item.id);
  }
  console.log(`[Wiki Scraper] Loaded ${tradeableMap.size} tradeable items.`);

  const runeliteMap = loadRuneLiteItemConstants();

  // 2. Discover ALL Boss, Raid, and Slayer pages
  console.log('[Wiki Scraper] Discovering strategy pages across OSRS Wiki categories...');
  const [embedded, strategies, slayerTasks] = await Promise.all([
    fetchJson('https://oldschool.runescape.wiki/api.php?action=query&list=embeddedin&eititle=Template:Recommended_equipment&eilimit=500&format=json'),
    fetchJson('https://oldschool.runescape.wiki/api.php?action=query&list=categorymembers&cmtitle=Category:Strategies&cmlimit=500&format=json'),
    fetchJson('https://oldschool.runescape.wiki/api.php?action=query&list=categorymembers&cmtitle=Category:Slayer_task&cmlimit=500&format=json')
  ]);

  const allPages = new Set();
  embedded.query.embeddedin.forEach(p => allPages.add(p.title));
  strategies.query.categorymembers.forEach(p => allPages.add(p.title));
  slayerTasks.query.categorymembers.forEach(p => allPages.add(p.title));

  const ignoredPatterns = ['Template:', 'User:', 'Guide:', 'Pay-to-play', 'training', 'Hunter', 'Prayer', 'Quest:'];
  const validPages = Array.from(allPages).filter(title => {
    if (ignoredPatterns.some(p => title.includes(p))) return false;
    return title.includes('/Strategies') || title.startsWith('Slayer task/');
  });

  console.log(`[Wiki Scraper] Found ${validPages.length} relevant strategy pages to scrape.`);

  const activities = {};

  // 3. Batch fetch wikitext (40 pages per request)
  for (let i = 0; i < validPages.length; i += 40) {
    const chunk = validPages.slice(i, i + 40);
    const titlesParam = encodeURIComponent(chunk.join('|'));
    const batchUrl = `https://oldschool.runescape.wiki/api.php?action=query&prop=revisions&rvprop=content&rvslots=main&titles=${titlesParam}&format=json`;

    try {
      const batchRes = await fetchJson(batchUrl);
      const pages = batchRes.query ? batchRes.query.pages : {};

      for (const pid of Object.keys(pages)) {
        const page = pages[pid];
        if (!page.revisions || !page.revisions[0]) continue;

        const title = page.title;
        const wikitext = page.revisions[0].slots.main['*'];
        const gearBlocks = extractTemplates(wikitext, 'Recommended equipment');
        if (gearBlocks.length === 0) continue;

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
        }

        // Clean up base name
        baseName = baseName.replace(/_/g, ' ').trim();

        // Parse each gear table template on the page
        for (let b = 0; b < gearBlocks.length; b++) {
          const block = gearBlocks[b];
          const lines = block.split('\n');

          let rawStyleVal = 'Melee';
          let combatStyle = 'Melee';
          const slots = {};

          for (const line of lines) {
            // Note: handles optional whitespace after pipe (e.g. '| style = ' or '|head1 = ')
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

          if (Object.keys(slots).length < 3) {
            continue; // Skip malformed or empty templates
          }

          // Format clean variant title
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

          // Avoid duplicate activity IDs
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

          activities[activityId] = {
            id: activityId,
            name: activityName,
            category: category,
            style: combatStyle,
            slots: slots,
            inventory: [
              { id: 2434, qty: 3, optional: false },
              { id: 391, qty: 8, optional: false }
            ]
          };
        }
      }
    } catch (batchErr) {
      console.warn(`[Wiki Scraper] Batch error around index ${i}:`, batchErr.message);
    }
  }

  const activityCount = Object.keys(activities).length;
  console.log(`[Wiki Scraper] Extracted ${activityCount} total setups across Bossing, Slayer, and Raids!`);

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

  console.log(`[Wiki Scraper] Successfully compiled and saved ${activityCount} activities to ${OUTPUT_RESOURCES} and ${OUTPUT_DATA}!`);
}

scrapeWikiGear().catch(err => {
  console.error('[Wiki Scraper] Fatal error:', err);
  process.exit(1);
});
