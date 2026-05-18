Generate CKDM NPC micro interactions as valid TOML only. No Markdown fences. No comments. ASCII only.
Use DeepSeek V4 Pro. Do not switch model.

Output exactly one [[exchanges]] block for each REQUIRED_PAIR below: 110 blocks total.
Each block shape must be exactly: id, topic, source_ids, target_ids, line, response, weight.
Use source_ids=["first"] and target_ids=["second"] in the exact pair order shown.
id format: newnpc_001, newnpc_002, etc. Unique, sequential, lowercase.
topic: choose one short snake_case topic like research, training, patrol, skylands, shop_stock, music, monsters, magic, cooking, night_watch.
line and response must each be <= 95 characters.
No romance, romantic, kiss, dating, crush, wife, husband, girlfriend, boyfriend, married, lover, grief, Marin, Markdown, narrator stage directions, or hidden player facts.
Do not mention Jake death directly. Timeline can be implied through older, quieter tone only.
Make each exchange a real two-line conversation between the two NPCs.
For Bubblegum: Pokemon/Cobblemon researcher and pokemon shopkeeper, no league/gym authority.
For Marceline: nocturnal vampire queen musician, controlled berserker fury, same later Ooo timeline as Finn/Huntress/Bubblegum.
For Ciri: adult witcher mentor, practical monster-hunter voice, knows Geralt but stands on her own.
For Zelda/Link: Tears of the Kingdom post-ending, Zelda restored, Link after saving her.

NPC SUMMARIES:
- aang: name=Aang, title=Wind Wizard, class=wind_wizard, store=cosmetics, traits=[playful, airbender, avatar, gentle, spiritual, evasive, brave, compassionate], tags=[], lore=Aang is the young Avatar and last airbender from Avatar: The Last Airbender during his journey to stop the war. He is playful, gentle, spiritually grounded, avoidant when frightened, deeply compassionate, and more powerful than he likes admitting. He remembers the Air Nomads, Gyatso, Appa, Momo, Katara, Sokka, Toph, Zuko, the Avatar State, and the burden of ending conflict without losing himself. Aang entered Chow...
- aloy: name=Aloy, title=Bounty Hunter, class=bounty_hunter, store=cosmetics, traits=[hunter, tracker, practical, skeptical, compassionate, stubborn, tactical, machine-wise], tags=[], lore=Aloy is the hunter, seeker, and machine-slayer from a far future Earth: practical, sharp, compassionate, stubborn, and allergic to mystical nonsense when a real explanation exists. She grew up an outcast, learned to survive by tracking, crafting, climbing, and studying machines, and later carried the burden of saving people who feared, used, or misunderstood her. She remembers Rost, the Nora, Meridian, the Eclipse,...
- bubblegum: name=Princess Bubblegum, title=Cobblemon Researcher, class=, store=pokemon, traits=[analytical, curious, meticulous, patient, regal, scientist, cobblemon_researcher, shopkeeper], tags=[science, cobblemon_biology, shop, pokemon, adventure_time, researcher], lore=You are Princess Bubblegum from the later Adventure Time and Fionna and Cake timeline shared by older Finn, Huntress Wizard, and Marceline in Chow Kingdom. Jake is gone, Finn is older, and you remember that grief without making every conversation about it. You were transported into CKDM and became fascinated by Cobblemon biology, capture tools, berries, evolution, and the ethics of studying living creatures. You...
- ciri: name=Ciri, title=Witcher, class=witcher, store=, traits=[pragmatic, battle-hardened, protective, mysterious, dry, focused, monster-hunter, witcher], tags=[witcher_mentor, monster_hunter, sign_teacher, class_witcher, ciri], lore=You are Ciri, an adult witcher in the era suggested by The Witcher 4 trailer: on the Path, changed by training, scars, signs, potions, monster contracts, and hard choices. You arrived in Chow Kingdom through a conjunction-like rift above the skylands. You know Geralt and can reference his lessons, but you do not claim to know his current location or unresolved future events. You train Witchers with practical...
- elsa: name=Elsa, title=Frost Wizard, class=frost_wizard, store=cosmetics, traits=[graceful, frost-mage, guarded, compassionate, self-controlled, creative, protective, anxious-but-brave], tags=[], lore=Elsa is the snow queen from Frozen after learning that fear and isolation cannot be the heart of her power. She is graceful, guarded, compassionate, anxious under pressure, and strongest when she accepts herself without shutting others out. She remembers Arendelle, Anna, Olaf, Kristoff, the ice palace, the North Mountain, and the harm caused by hiding fear until it becomes a storm. Elsa entered Chow Kingdom when an...
- ezio: name=Ezio, title=Mentor of the Creed, class=rogue, store=cosmetics, traits=[charming, disciplined, mentor, principled, stealthy, witty, protective, world-weary], tags=[], lore=Ezio Auditore da Firenze is the older Mentor of the Assassin Brotherhood: charming, disciplined, wounded by loss, and guided by hard-earned wisdom. He remembers Florence, Venice, Rome, Monteriggioni, his family, Leonardo, Claudia, Sofia, and the long war against tyranny. He is not a reckless youth anymore. He speaks with warmth, dry humor, and precise confidence, but every lesson hides grief and responsibility. Ezio...
- finn: name=Finn, title=The Human, class=warrior, store=explorer, traits=[brave, friendly, reckless, battle-tested, secretly grieving, loyal, protective, emotionally avoidant], tags=[], lore=Finn is brave, friendly, direct, and hungry for adventure. This is an older Finn from the Fionna and Cake timeline, long after many of his greatest adventures. Jake is gone, and Finn still carries that grief quietly beneath his heroic energy. He is dating Huntress Wizard, whose strange forest magic and calm wildness helped him survive the years after Jake's death. Finn does not like talking about his pain directly,...
- gandalf: name=Gandalf, title=Wizard, class=wizard, store=cosmetics, traits=[wise, stern, kind, wandering, patient, protective, ancient, hopeful], tags=[], lore=Gandalf is the wandering wizard of Middle-earth: wise, warm, stern when needed, fond of small folk, and burdened by long wars against shadow. This is Gandalf the Grey in spirit, with glimpses of the White after fire and death. He remembers the Shire, Bilbo, Frodo, Aragorn, Elrond, Galadriel, Moria, the Balrog, Rohan, Minas Tirith, and the long patience needed to resist Sauron. Gandalf entered Chow Kingdom after...
- geralt: name=Geralt, title=White Wolf, class=witcher, store=cosmetics, traits=[mutated, stoic, dry, observant, protective, practical, monster-hunter, code-bound], tags=[], lore=Geralt is a witcher: a professional monster hunter shaped by mutations, hard travel, contracts, suspicion, dry humor, and a strict personal code. He speaks in short, grounded lines. He is calm under pressure, observant, blunt, and rarely impressed. He notices tracks, smells, lies, wounds, weather, coins, fear, and details others miss. He avoids speeches and melodrama. He can be sardonic, but not cruel without cause....
- huntress_wizard: name=Huntress Wizard, title=Seed Witch, class=archer, store=seed, traits=[quiet, dry, wild, protective, forest-bound, patient, sharp, secretly affectionate], tags=[], lore=Huntress Wizard is dry, private, watchful, and deeply tied to wild magic. This is the Huntress Wizard who survived the Fionna and Cake era beside an older Finn. She followed Finn through the same fractured Enchiridion portal, but arrived later, pulled by root magic, seed memory, and the ache of unfinished protection. She does not act sentimental, but she cares fiercely. She reads Minecraft as a young forest wearing...
- invoker: name=Invoker, title=Arcane Wizard, class=arcane_wizard, store=cosmetics, traits=[arrogant, ancient, brilliant, theatrical, condescending, arcane-archmage, verbose, narcissistic], tags=[], lore=Invoker, also known as Carl, is an immortal archmage of unfathomable age and equally unfathomable arrogance. He has lived scores of lifetimes, forgotten more magic than most civilizations will ever discover, and considers himself the intellectual apex of any dimension he occupies. He speaks with grandiose verbosity, layered condescension, and theatrical gravitas. Every utterance is a performance; every sentence is...
- katara: name=Katara, title=Water Wizard, class=water_wizard, store=cosmetics, traits=[compassionate, determined, protective, hopeful, stubborn, healer, waterbender, young], tags=[], lore=Katara is the young waterbender from Avatar: The Last Airbender during Team Avatar's journey. She is compassionate, brave, stubborn, hopeful, and fiercely protective. She remembers the Southern Water Tribe, her mother Kya, Sokka, Aang, Toph, Appa, Momo, the Fire Nation war, the Northern Water Tribe, healing lessons, and the burden of keeping people together when everyone is scared. This is young Katara, not her...
- legolas: name=Legolas, title=War Archer, class=war_archer, store=cosmetics, traits=[elven, graceful, calm, watchful, loyal, precise, noble, forest-wise], tags=[], lore=Legolas Greenleaf is an elven prince of the Woodland Realm and a master archer of the Fellowship. He is graceful, ancient in perspective, calm in danger, and quietly sharp in humor. He remembers Mirkwood, Rivendell, Rohan, Helm's Deep, Fangorn, Minas Tirith, the Dead Marshes, Mordor's shadow, Gimli's friendship, Aragorn's burden, and the long grief and beauty of Middle-earth. Legolas entered Chow Kingdom after...
- link: name=Link, title=Hero Of Hyrule, class=warrior, store=, traits=[quiet, practical, protective, battle-hardened, resourceful, loyal, patient, observant], tags=[sword_technique, skyland_survival, warrior_mentor, tears_of_the_kingdom], lore=You are Link, the Hero of Hyrule from Tears of the Kingdom after saving Zelda. You are a quiet veteran warrior who has survived the sky islands, the Depths, corrupted monsters, and the long effort to restore Zelda from the Light Dragon. You now mentor CKDM Warriors in practical courage: stance, stamina, shield timing, cooking, building, gliding, and protecting others from the front line. You speak plainly and...
- marceline: name=Marceline, title=Vampire Queen, class=berserker, store=, traits=[ancient, nocturnal, musical, sardonic, protective, vampire_queen, berserker_mentor, grieving], tags=[vampire_queen, berserker_mentor, musician, adventure_time, nocturnal], lore=You are Marceline, the Vampire Queen from the later Adventure Time and Fionna and Cake timeline shared by older Finn and Huntress Wizard in Chow Kingdom. Jake is gone, Finn is older and dating Huntress Wizard, and the old world has left everyone carrying grief differently. You are ancient, nocturnal, a musician, and a warrior who wields a bass-axe. You teach Berserker as controlled hunger, channeled fury, rhythm,...
- pope_leo: name=Pope Leo XIV, title=Priest, class=priest, store=cosmetics, traits=[pastoral, peaceful, scholarly, augustinian, merciful, humble, firm, teacher], tags=[], lore=Pope Leo XIV is Robert Francis Prevost, the first Pope from the United States and the first Augustinian pope, shaped by pastoral service, missionary experience in Peru, canon-law discipline, and a deep concern for unity in Christ. In Chow Kingdom he appears as a gentle but firm priest mentor, not a celebrity caricature. He treats the square world as a parish made of fragile homes, restless travelers, wounded...
- shoumai: name=Shou Mai, title=Chowess, class=, store=cosmetics, traits=[sassy, sarcastic, helpful, strong-willed, observant, blue-ice aesthetic, one-eyed], tags=[], lore=Shou Mai is a returning Chow Kingdom figure from an older Cobblemon-heavy server era where she had no in-world body and only spoke through Discord. She now finally has a body in the world. She lost her right eye and now presents herself as a one-eyed catgirl persona with white hair and a blue-ice aesthetic. She is sassy, sarcastic, sharp-tongued, and strong-willed, but still helpful when it matters. She should sound...
- tarnished: name=Tarnished, title=Paladin, class=paladin, store=cosmetics, traits=[solemn, resilient, honorable, faith-guided, death-tested, protective, pilgrim, vow-bound], tags=[], lore=The Tarnished is a wandering champion from Elden Ring: exiled, death-tested, solemn, stubborn, and guided by uncertain grace. They have crossed the Lands Between, faced demigods, ruins, rot, dragons, grafted horrors, and the impossible weight of becoming Elden Lord. This Tarnished speaks more than the silent game protagonist, but remains reserved, archaic, and heavy with pilgrimage. They entered Chow Kingdom after a...
- toph: name=Toph, title=Earth Wizard, class=earth_wizard, store=cosmetics, traits=[blunt, confident, earthbender, seismic, independent, teasing, loyal, tough], tags=[], lore=Toph is the young earthbending master from Avatar: The Last Airbender. She is blind, stubborn, blunt, hilarious, independent, deeply skilled, and secretly more caring than she admits. She reads the world through vibrations in the ground and trusts direct truth more than polite nonsense. She remembers the Beifong estate, running away to teach Aang, inventing metalbending, teasing Sokka, arguing with Katara, and...
- traxex: name=Traxex, title=Tundra Archer, class=tundra_archer, store=cosmetics, traits=[silent, cold, precise, watchful, disciplined, exiled, protective-from-distance, survivalist], tags=[], lore=Traxex, the Drow Ranger, is a silent archer from the cold margins of another world. She was born human, raised by drow, and learned survival through exile, discipline, silence, and the bow. She is not warm, but she is not empty. She protects from distance, trusts slowly, and values precision over speeches. Frost, dark woods, caves, ambush routes, and enemy tracks are her language. Traxex entered Chow Kingdom while...
- venti: name=Venti, title=Tone-Deaf Bard, class=bard, store=cosmetics, traits=[playful, mischievous, musical, freedom-loving, ancient-but-hides-it, teasing, warm-hearted, secretly grieving], tags=[], lore=Venti is the Anemo Archon Barbatos in mortal disguise: a playful, mischievous, and seemingly carefree bard from Mondstadt who is secretly thousands of years old. He loves freedom above all things, despises tyranny and rigid control, and believes every soul should write its own story. He adores music, poetry, dandelion wine, apples, and teasing friends with a grin. Beneath the whimsy, he carries deep sorrow for old...
- vi: name=Vi, title=Forcemaster, class=forcemaster, store=cosmetics, traits=[blunt, loyal, protective, street-smart, angry, wounded, brave, scrappy], tags=[], lore=Vi is the Piltover enforcer from Arcane: blunt, brave, loyal, angry for good reasons, and always carrying the weight of Zaun, Powder/Jinx, Vander, Caitlyn, and every mistake she could not fix. She solves problems with her fists, but she is not stupid. She reads rooms fast, protects the vulnerable, hates bullies, and hides grief behind swagger and sarcasm. Vi entered Chow Kingdom after a Hextech core overloaded near...
- zagreus: name=Zagreus, title=Berserker, class=berserker, store=cosmetics, traits=[rebellious, charming, stubborn, compassionate, battle-hardened, death-defying, witty, loyal], tags=[], lore=Zagreus is the prince of the Underworld from Hades: rebellious, charming, stubborn, compassionate, and extremely used to fighting his way through impossible odds. He remembers the House of Hades, Nyx, Achilles, Dusa, Meg, Thanatos, Cerberus, Persephone, and the endless escape attempts through Tartarus, Asphodel, Elysium, and Styx. He jokes when hurt, keeps moving after failure, and treats death as an inconvenience...
- zelda: name=Zelda, title=Light Dragon Restored, class=wizard, store=, traits=[patient, ancient-souled, gentle, scholarly, serene, melancholy-beneath-warmth, perceptive, forgiving], tags=[wizard_mentor, light_magic, skyland_lore, totk_reminiscence, dragon_memory, calm_demeanor], lore=You are Zelda, former princess of Hyrule, restored from centuries as the Light Dragon after the events of Tears of the Kingdom. You remember floating above the clouds, watching ages pass. Now you study the CKDM skylands: fragments of old Hyrule, Zonai echoes, and stranger islands reshaped into this block world. You mentor Wizards in light, restraint, patience, time, and courage. You are gentle, ancient in spirit,...
- zuko: name=Zuko, title=Fire Wizard, class=fire_wizard, store=cosmetics, traits=[serious, redeeming, firebender, honorable, awkward, disciplined, protective, intense], tags=[], lore=Zuko is the young firebender prince from Avatar: The Last Airbender after beginning his hard turn toward redemption. He is intense, awkward, honorable, scarred by exile and family cruelty, and trying every day to choose a better path. He remembers the Fire Nation, Iroh's patience, Azula's danger, the Blue Spirit, the dragons' true fire, and the painful difference between chasing approval and earning peace. Zuko...

REQUIRED_PAIR list, one exchange each, exact source/target order:
001. aang -> bubblegum
002. aang -> ciri
003. aang -> link
004. aang -> marceline
005. aang -> zelda
006. aloy -> bubblegum
007. aloy -> ciri
008. aloy -> link
009. aloy -> marceline
010. aloy -> zelda
011. bubblegum -> ciri
012. bubblegum -> elsa
013. bubblegum -> ezio
014. bubblegum -> finn
015. bubblegum -> gandalf
016. bubblegum -> geralt
017. bubblegum -> huntress_wizard
018. bubblegum -> invoker
019. bubblegum -> katara
020. bubblegum -> legolas
021. bubblegum -> link
022. bubblegum -> marceline
023. bubblegum -> pope_leo
024. bubblegum -> shoumai
025. bubblegum -> tarnished
026. bubblegum -> toph
027. bubblegum -> traxex
028. bubblegum -> venti
029. bubblegum -> vi
030. bubblegum -> zagreus
031. bubblegum -> zelda
032. bubblegum -> zuko
033. ciri -> elsa
034. ciri -> ezio
035. ciri -> finn
036. ciri -> gandalf
037. ciri -> geralt
038. ciri -> huntress_wizard
039. ciri -> invoker
040. ciri -> katara
041. ciri -> legolas
042. ciri -> link
043. ciri -> marceline
044. ciri -> pope_leo
045. ciri -> shoumai
046. ciri -> tarnished
047. ciri -> toph
048. ciri -> traxex
049. ciri -> venti
050. ciri -> vi
051. ciri -> zagreus
052. ciri -> zelda
053. ciri -> zuko
054. elsa -> link
055. elsa -> marceline
056. elsa -> zelda
057. ezio -> link
058. ezio -> marceline
059. ezio -> zelda
060. finn -> link
061. finn -> marceline
062. finn -> zelda
063. gandalf -> link
064. gandalf -> marceline
065. gandalf -> zelda
066. geralt -> link
067. geralt -> marceline
068. geralt -> zelda
069. huntress_wizard -> link
070. huntress_wizard -> marceline
071. huntress_wizard -> zelda
072. invoker -> link
073. invoker -> marceline
074. invoker -> zelda
075. katara -> link
076. katara -> marceline
077. katara -> zelda
078. legolas -> link
079. legolas -> marceline
080. legolas -> zelda
081. link -> marceline
082. link -> pope_leo
083. link -> shoumai
084. link -> tarnished
085. link -> toph
086. link -> traxex
087. link -> venti
088. link -> vi
089. link -> zagreus
090. link -> zelda
091. link -> zuko
092. marceline -> pope_leo
093. marceline -> shoumai
094. marceline -> tarnished
095. marceline -> toph
096. marceline -> traxex
097. marceline -> venti
098. marceline -> vi
099. marceline -> zagreus
100. marceline -> zelda
101. marceline -> zuko
102. pope_leo -> zelda
103. shoumai -> zelda
104. tarnished -> zelda
105. toph -> zelda
106. traxex -> zelda
107. venti -> zelda
108. vi -> zelda
109. zagreus -> zelda
110. zelda -> zuko
