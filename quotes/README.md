# Quranic Tafsir Quotes Database

## Overview
This repository contains a rich and comprehensive database of 560 Islamic quotes derived from the Tafsir (exegesis) of the Holy Quran, primarily referencing the profound works of esteemed scholars such as Ibn Kathir and As-Sa'di. 

Spanning the entirety of the Quran from Surah Al-Fatihah (Juz 1) to Surah An-Nas (Juz 30), this collection was thoughtfully curated to serve as the core content for a daily reminder widget application.

## Available Languages
The database is fully localized and available in three languages to reach a wider audience:
- **Indonesian (ID):** Primary reflections and wisdom.
- **Arabic (AR):** Original script or related thematic Arabic text.
- **English (EN):** Global accessible translations and reflections.

## Data Formats & Structure
The data is provided in two formats for maximum flexibility:

### 1. JSON Format
Ideal for web and mobile applications using NoSQL or direct object mapping.

### 2. SQLite Database (`quotes.sqlite`)
A relational database file ready for production use. The table structure is as follows:

| Column | Data Type | Description |
| :--- | :--- | :--- |
| `id` | INTEGER | Unique identifier for each quote. |
| `lang` | VARCHAR | Language code (e.g., 'id', 'ar', 'en'). |
| `quote` | TEXT | The core message or spiritual reflection. |
| `reference` | TEXT | Surah name, verse number, and Tafsir source. |

## Content Description
The quotes within this database are contextualized reflections extracted from classical Tafsir. They cover a wide array of life aspects, including:
- **Aqidah (Faith & Creed):** Reminders of Allah's oneness and His majestic attributes.
- **Akhlaq (Morality & Character):** Advice on patience, sincerity, and humility.
- **Mu'amalah (Social Interactions):** Guidance on kindness to parents, orphans, and neighbors.
- **Tazkiyatun Nafs (Purification of the Soul):** Admonitions about the fleeting nature of the world.

## Use Cases
This dataset is perfectly suited for:
- Daily Islamic reminder widgets (iOS/Android).
- Multilingual automated bots for Telegram, WhatsApp, or Discord.
- Social media content automation for spiritual reminders.
- Personal reflection and journaling applications.

## Acknowledgment
May this collection serve as a continuous charity (*Sadaqah Jariyah*). We hope that these timeless words of wisdom bring immense spiritual benefit, comfort, and guidance to all users across different languages.
