const CATEGORIES = ['Anytime', 'Before Fajr', 'Fajr', 'Shuruq', 'Dhuhr', 'Asr', 'Maghrib', 'Isha'];
const WEEKDAYS = [{l: 'M', v: 1}, {l: 'T', v: 2}, {l: 'W', v: 3}, {l: 'T', v: 4}, {l: 'F', v: 5}, {l: 'S', v: 6}, {l: 'S', v: 7}];

const TYPE_COLORS = {
  dua: { bg: 'rgba(46, 204, 113, 0.12)', border: '#2ecc71', text: '#2ecc71', shadow: '0 4px 12px rgba(46, 204, 113, 0.15)' },
  adkar: { bg: 'rgba(155, 89, 182, 0.12)', border: '#9b59b6', text: '#9b59b6', shadow: '0 4px 12px rgba(155, 89, 182, 0.15)' },
  prayer: { bg: 'rgba(52, 152, 219, 0.12)', border: '#3498db', text: '#3498db', shadow: '0 4px 12px rgba(52, 152, 219, 0.15)' },
  reading: { bg: 'rgba(241, 196, 15, 0.12)', border: '#f1c40f', text: '#f1c40f', shadow: '0 4px 12px rgba(241, 196, 15, 0.15)' },
  other: { bg: 'rgba(149, 165, 166, 0.12)', border: '#95a5a6', text: '#95a5a6', shadow: '0 4px 12px rgba(149, 165, 166, 0.15)' }
};

const QuickButton = ({ onClick, children, color }) => (
  <button 
    className="quick-add-btn"
    onClick={(e) => { e.stopPropagation(); onClick(); }}
    style={{
      padding: '4px 8px', backgroundColor: 'transparent', color: color,
      border: `1px solid ${color}`, borderRadius: '6px', fontWeight: '600', fontSize: '0.85em',
      cursor: 'pointer', flex: 1, textAlign: 'center'
    }}>
    {children}
  </button>
);

const HabitCardRow = ({ habit, dayData, toggleHabit, incrementDuration, activeQuickAdd, setActiveQuickAdd, showItemSettings, onEdit, onMove, onToggleSleep }) => {
  const isCompleted = !!dayData[habit.id]; 
  const currentDuration = typeof dayData[habit.id] === 'number' ? dayData[habit.id] : 0;
  
  const isQuickAdding = activeQuickAdd === habit.id;
  const isSleeping = habit.status === 'sleeping';
  const style = TYPE_COLORS[habit.type] || TYPE_COLORS['other'];
  const step = habit.step || 25; 

  const handleToggle = (e) => { 
    if (showItemSettings) return; 
    e.stopPropagation(); 
    if (isQuickAdding) setActiveQuickAdd(null);
    else toggleHabit(habit.id); 
  };

  const handleDurationClick = (e) => { 
    if (showItemSettings) return;
    e.stopPropagation(); 
    setActiveQuickAdd(isQuickAdding ? null : habit.id); 
  };

  return (
    <div 
      className="habit-card"
      onClick={handleToggle}
      style={{
        display: 'flex', flexDirection: 'column', padding: '10px 14px',
        backgroundColor: isCompleted ? style.bg : 'var(--background-primary)',
        border: `1px solid ${isCompleted ? style.border : 'var(--background-modifier-border)'}`,
        // Sleek color indicator ribbon on the left when incomplete
        borderLeft: (!isCompleted && !isSleeping) ? `4px solid ${style.border}` : `1px solid ${isCompleted ? style.border : 'var(--background-modifier-border)'}`,
        boxShadow: (isCompleted && !isSleeping) ? style.shadow : 'none',
        opacity: isSleeping ? 0.45 : 1, 
        filter: isSleeping ? 'grayscale(100%)' : 'none',
        borderRadius: '10px', cursor: showItemSettings ? 'default' : 'pointer',
        marginBottom: '8px'
      }}>
      
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flex: 1, overflow: 'hidden' }}>
          <div className={`habit-checkbox ${isCompleted ? 'checked' : ''}`} style={{ 
            width: '20px', height: '20px', borderRadius: '50%', 
            border: `2px solid ${isCompleted ? style.border : 'var(--text-muted)'}`,
            backgroundColor: isCompleted ? style.border : 'transparent',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: 'var(--background-primary)', flexShrink: 0
          }}>
            {isCompleted && <span style={{ fontSize: '12px', fontWeight: 'bold' }}>✓</span>}
          </div>
          
          <div style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <span style={{ fontWeight: isCompleted ? '600' : '500', fontSize: '0.95em', color: isCompleted ? 'var(--text-normal)' : 'var(--text-muted)', whiteSpace: 'nowrap', textOverflow: 'ellipsis', overflow: 'hidden' }}>
              {habit.label}
            </span>
            {/* Description rendering removed for zero-clutter interface */}
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}>
          {!showItemSettings && habit.unit && (
            <div className="duration-pill" onClick={handleDurationClick} style={{ padding: '2px 8px', backgroundColor: isCompleted ? 'var(--background-primary)' : 'var(--background-secondary)', borderRadius: '12px', fontWeight: '600', fontSize: '0.8em', color: style.text, border: `1px solid ${isCompleted ? 'transparent' : 'var(--background-modifier-border)'}` }}>
               {currentDuration} {habit.unit}
            </div>
          )}
          
          {showItemSettings && (
            <div style={{ display: 'flex', gap: '4px' }}>
              <button className="settings-btn" onClick={(e) => { e.stopPropagation(); onToggleSleep(habit); }} title={isSleeping ? "Wake Up" : "Put to Sleep"} style={{ background: 'transparent', border: '1px solid var(--background-modifier-border)', borderRadius: '6px', cursor: 'pointer', padding: '4px 8px', color: 'var(--text-normal)' }}>{isSleeping ? '☀️' : '🌙'}</button>
              <button className="settings-btn" onClick={(e) => { e.stopPropagation(); onMove(habit, -1); }} style={{ background: 'transparent', border: '1px solid var(--background-modifier-border)', borderRadius: '6px', cursor: 'pointer', padding: '4px 8px', color: 'var(--text-normal)' }}>↑</button>
              <button className="settings-btn" onClick={(e) => { e.stopPropagation(); onMove(habit, 1); }} style={{ background: 'transparent', border: '1px solid var(--background-modifier-border)', borderRadius: '6px', cursor: 'pointer', padding: '4px 8px', color: 'var(--text-normal)' }}>↓</button>
              <button className="settings-btn" onClick={(e) => { e.stopPropagation(); onEdit(habit); }} style={{ background: 'var(--interactive-accent)', border: 'none', borderRadius: '6px', color: 'var(--text-on-accent)', cursor: 'pointer', padding: '4px 10px', fontSize: '0.85em', fontWeight: '600' }}>Edit</button>
            </div>
          )}
        </div>
      </div>

      {isQuickAdding && !showItemSettings && habit.unit && (
        <div onClick={(e) => e.stopPropagation()} style={{ 
          marginTop: '10px', paddingTop: '10px', borderTop: `1px dashed ${style.border}`,
          display: 'flex', gap: '6px', flexWrap: 'nowrap', alignItems: 'center'
        }}>
          <QuickButton onClick={() => incrementDuration(habit.id, -step * 2)} color="var(--text-muted)">-{step * 2}</QuickButton>
          <QuickButton onClick={() => incrementDuration(habit.id, -step)} color="var(--text-muted)">-{step}</QuickButton>
          <QuickButton onClick={() => incrementDuration(habit.id, step)} color={style.text}>+{step}</QuickButton>
          <QuickButton onClick={() => incrementDuration(habit.id, step * 2)} color={style.text}>+{step * 2}</QuickButton>
        </div>
      )}
    </div>
  );
};

// --- Main App ---

function HabitTracker() {
  const [habits, setHabits] = dc.useState([]);
  const [selectedDateObj, setSelectedDateObj] = dc.useState(dc.luxon.DateTime.now().startOf('day'));
  const [activeQuickAdd, setActiveQuickAdd] = dc.useState(null); 
  const [editingHabit, setEditingHabit] = dc.useState(null);
  
  const [dailyNotesPath, setDailyNotesPath] = dc.useState("System/Journal/Daily Notes");
  const [autoScroll, setAutoScroll] = dc.useState(true);
  const [showItemSettings, setShowItemSettings] = dc.useState(false);
  const [dayData, setDayData] = dc.useState({});
  const [configPath, setConfigPath] = dc.useState(null);
  const [prayerTimes, setPrayerTimes] = dc.useState(null);
  
  const categoryRefs = dc.useRef({});

  const selectedDateStr = selectedDateObj.toFormat('yyyy-MM-dd');
  const yearMonthStr = selectedDateObj.toFormat('yyyy/MM');
  const currentNotePath = `${dailyNotesPath}/${yearMonthStr}/${selectedDateStr}.md`;
  
  const isToday = selectedDateObj.hasSame(dc.luxon.DateTime.now(), 'day');

  // Load config
  dc.useEffect(() => {
    try {
      const file = app.workspace.getActiveFile();
      if (file) {
        setConfigPath(file.path);
        app.fileManager.processFrontMatter(file, (fm) => {
          if (fm.daily_notes_path) setDailyNotesPath(fm.daily_notes_path);
          if (fm.auto_scroll !== undefined) setAutoScroll(fm.auto_scroll);
          if (fm.habit_config && Array.isArray(fm.habit_config)) setHabits(fm.habit_config);
        });
      }
    } catch (error) { console.error(error); }
  }, []);

  // Fetch Prayer Times
  dc.useEffect(() => {
    const fetchPrayerTimes = async () => {
      const file = app.vault.getAbstractFileByPath("Templates/Prayer Times.md");
      if (file) {
        const content = await app.vault.read(file);
        const times = {};
        const regex = /\|\s*(Fajr|Sunrise|Dhuhr|Asr|Maghrib|Isha)\s*\|\s*([^|]+)\s*\|/gi;
        let match;
        while ((match = regex.exec(content)) !== null) {
          times[match[1].trim()] = match[2].replace(/\*/g, '').trim();
        }
        setPrayerTimes(times);
      }
    };
    fetchPrayerTimes();
  }, []);

  // Auto-Scroll Logic
  dc.useEffect(() => {
    if (autoScroll && prayerTimes && isToday && !showItemSettings) {
      const getActiveCategory = () => {
        const now = dc.luxon.DateTime.now();
        const parseT = (tStr) => tStr ? dc.luxon.DateTime.fromFormat(tStr, "h:mm a") : null;
        
        const f = parseT(prayerTimes['Fajr']);
        const s = parseT(prayerTimes['Sunrise']);
        const d = parseT(prayerTimes['Dhuhr']);
        const a = parseT(prayerTimes['Asr']);
        const m = parseT(prayerTimes['Maghrib']);
        const i = parseT(prayerTimes['Isha']);

        if (!f || !s || !d || !a || !m || !i) return null;
        
        if (now < f) return 'Before Fajr';
        if (now >= f && now < s) return 'Fajr';
        if (now >= s && now < d) return 'Shuruq';
        if (now >= d && now < a) return 'Dhuhr';
        if (now >= a && now < m) return 'Asr';
        if (now >= m && now < i) return 'Maghrib';
        if (now >= i) return 'Isha';
        return 'Anytime';
      };

      const activeCat = getActiveCategory();
      if (activeCat && categoryRefs.current[activeCat]) {
        setTimeout(() => {
          categoryRefs.current[activeCat].scrollIntoView({ behavior: 'smooth', block: 'start' });
        }, 500);
      }
    }
  }, [prayerTimes, autoScroll, selectedDateStr, showItemSettings, isToday]);

  const saveConfig = async (key, value) => {
    const path = configPath || app.workspace.getActiveFile()?.path;
    if (path) {
      const file = app.vault.getAbstractFileByPath(path);
      if (file) {
        await app.fileManager.processFrontMatter(file, (fm) => { fm[key] = value; });
      }
    }
  };

  const saveHabits = async (newHabits) => {
    setHabits(newHabits);
    await saveConfig('habit_config', newHabits);
  };

  const handlePathChange = async () => {
    const newPath = prompt("Enter the root folder path for your daily notes (without trailing slash):", dailyNotesPath);
    if (newPath !== null && newPath.trim() !== "") {
      const cleanPath = newPath.trim().replace(/\/$/, "");
      setDailyNotesPath(cleanPath);
      await saveConfig('daily_notes_path', cleanPath);
    }
  };

  const migrateTrueToDuration = async () => {
    if(!confirm("This will scan your Daily Notes and replace 'true' values with the current default duration for your habits. Continue?")) return;
    let count = 0;
    const files = app.vault.getMarkdownFiles().filter(f => f.path.startsWith(dailyNotesPath));
    for (const file of files) {
      await app.fileManager.processFrontMatter(file, (fm) => {
        if (fm.habits) {
          let changed = false;
          for (const key of Object.keys(fm.habits)) {
            if (fm.habits[key] === true) {
              const habitConfig = habits.find(h => h.id === key);
              if (habitConfig && habitConfig.defaultDuration) {
                fm.habits[key] = habitConfig.defaultDuration;
                changed = true;
              }
            }
          }
          if (changed) count++;
        }
      });
    }
    alert(`Migration complete! Updated 'true' values to default durations in ${count} daily notes.`);
  };

  dc.useEffect(() => {
    let isMounted = true;
    const loadDayData = () => {
      const file = app.vault.getAbstractFileByPath(currentNotePath); 
      if (file) {
        const cache = app.metadataCache.getFileCache(file);
        if (isMounted) setDayData(cache?.frontmatter?.habits || {});
      } else {
        if (isMounted) setDayData({}); 
      }
    };
    loadDayData();

    const onMetadataChange = (file, data, cache) => {
      if (file.path === currentNotePath && isMounted) setDayData(cache?.frontmatter?.habits || {});
    };
    app.metadataCache.on('changed', onMetadataChange);
    return () => {
      isMounted = false;
      app.metadataCache.off('changed', onMetadataChange);
    };
  }, [selectedDateStr, dailyNotesPath, currentNotePath]);

  async function updateFileHabits(updateCallback) {
    let file = app.vault.getAbstractFileByPath(currentNotePath);
    if (!file) {
      try {
        const targetFolder = `${dailyNotesPath}/${yearMonthStr}`;
        if (!app.vault.getAbstractFileByPath(targetFolder)) {
          const folderParts = targetFolder.split('/');
          let pathBuilder = '';
          for (const part of folderParts) {
            pathBuilder = pathBuilder === '' ? part : `${pathBuilder}/${part}`;
            if (!app.vault.getAbstractFileByPath(pathBuilder)) await app.vault.createFolder(pathBuilder);
          }
        }
        file = await app.vault.create(currentNotePath, "---\nhabits:\n---\n");
        await new Promise(r => setTimeout(r, 50)); 
      } catch (err) { console.error(err); return; }
    }
    
    setDayData(prev => {
      const next = { ...prev };
      updateCallback(next);
      return next;
    });
    
    await app.fileManager.processFrontMatter(file, (fm) => {
      if (!fm.habits || typeof fm.habits !== 'object' || fm.habits === null) fm.habits = {};
      updateCallback(fm.habits);
    });
  }

  const toggleHabit = (habitId) => {
    const habit = habits.find(h => h.id === habitId);
    updateFileHabits((habitsData) => {
      if (!!habitsData[habitId]) delete habitsData[habitId];
      else habitsData[habitId] = habit?.defaultDuration ? habit.defaultDuration : true;
    });
  };

  const incrementDuration = (habitId, amount) => {
    updateFileHabits((habitsData) => {
      let currentDur = typeof habitsData[habitId] === 'number' ? habitsData[habitId] : 0;
      const newDur = Math.max(0, currentDur + amount);
      if (newDur > 0) habitsData[habitId] = newDur;
      else delete habitsData[habitId];
    });
  };

  const moveHabit = (habit, direction) => {
    let newHabits = [...habits];
    const index = newHabits.findIndex(h => h.id === habit.id);
    if (index === -1) return;

    const groups = CATEGORIES.map(cat => newHabits.filter(h => h.category === cat));
    const catIndex = CATEGORIES.indexOf(habit.category);
    const group = groups[catIndex];
    const indexInGroup = group.findIndex(h => h.id === habit.id);

    if (direction === -1) { 
        if (indexInGroup > 0) [group[indexInGroup], group[indexInGroup - 1]] = [group[indexInGroup - 1], group[indexInGroup]];
        else if (catIndex > 0) {
            habit.category = CATEGORIES[catIndex - 1];
            groups[catIndex - 1].push(habit);
            groups[catIndex].splice(indexInGroup, 1);
        }
    } else { 
        if (indexInGroup < group.length - 1) [group[indexInGroup], group[indexInGroup + 1]] = [group[indexInGroup + 1], group[indexInGroup]];
        else if (catIndex < CATEGORIES.length - 1) {
            habit.category = CATEGORIES[catIndex + 1];
            groups[catIndex + 1].unshift(habit);
            groups[catIndex].splice(indexInGroup, 1);
        }
    }
    saveHabits(groups.flat());
  };

  const handleSaveHabit = (e) => {
    e.preventDefault();
    const updatedHabits = habits.find(h => h.id === editingHabit.id) 
      ? habits.map(h => h.id === editingHabit.id ? editingHabit : h) 
      : [...habits, { ...editingHabit, id: `habit_${Date.now()}` }];
    saveHabits(updatedHabits);
    setEditingHabit(null);
  };

  const deleteHabit = (id) => { if(confirm("Delete this habit permanently?")) saveHabits(habits.filter(h => h.id !== id)); };
  const toggleHabitSleep = (habit) => { saveHabits(habits.map(h => h.id === habit.id ? { ...h, status: h.status === 'sleeping' ? 'active' : 'sleeping' } : h)); };

  const renderTracker = () => {
    const visibleHabits = habits.filter(h => {
      if (showItemSettings) return true; 
      if (h.status === 'sleeping') return false;
      const freqType = h.frequencyType || 'daily';
      if (freqType === 'weekly') {
        const allowedDays = h.recurringDays || [1,2,3,4,5,6,7];
        return allowedDays.includes(selectedDateObj.weekday);
      } else if (freqType === 'interval') {
        if (!h.intervalStart) return true;
        const start = dc.luxon.DateTime.fromISO(h.intervalStart).startOf('day');
        const diffDays = Math.round(selectedDateObj.diff(start, 'days').days);
        const interval = h.intervalDays || 1;
        return diffDays >= 0 && (diffDays % interval === 0);
      }
      return true; 
    });

    return (
      <div onClick={() => setActiveQuickAdd(null)}>
        {/* Settings Bar */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '16px', padding: '0 4px' }}>
          <button 
            onClick={() => setShowItemSettings(!showItemSettings)} 
            style={{ background: 'transparent', border: 'none', color: showItemSettings ? 'var(--text-accent)' : 'var(--text-muted)', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px', fontWeight: '600', fontSize: '0.85em' }}
          >
            ⚙️ {showItemSettings ? 'Done Editing' : 'Edit Habits'}
          </button>
        </div>

        {showItemSettings && (
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '24px', background: 'var(--background-secondary)', padding: '12px', borderRadius: '8px' }}>
            <button onClick={() => setEditingHabit({ frequencyType: 'daily', status: 'active', category: 'Anytime', type: 'dua', step: 25 })} style={{ padding: '6px 12px', backgroundColor: 'var(--interactive-accent)', color: 'var(--text-on-accent)', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: '600', fontSize: '0.85em' }}>+ Add Habit</button>
            <button onClick={migrateTrueToDuration} style={{ padding: '6px 12px', backgroundColor: 'transparent', color: 'var(--text-normal)', border: '1px solid var(--background-modifier-border)', borderRadius: '6px', cursor: 'pointer', fontWeight: '600', fontSize: '0.85em' }}>🔄 Convert 'True'</button>
            <button onClick={handlePathChange} style={{ padding: '6px 12px', backgroundColor: 'transparent', color: 'var(--text-normal)', border: '1px solid var(--background-modifier-border)', borderRadius: '6px', cursor: 'pointer', fontWeight: '600', fontSize: '0.85em' }}>📁 Set Path</button>
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--text-normal)', fontSize: '0.85em', cursor: 'pointer', marginLeft: 'auto' }}>
              <input type="checkbox" checked={autoScroll} onChange={(e) => { setAutoScroll(e.target.checked); saveConfig('auto_scroll', e.target.checked); }} />
              Auto-scroll to current time
            </label>
          </div>
        )}

        {/* Date Navigator */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', backgroundColor: 'var(--background-secondary)', padding: '10px 14px', borderRadius: '12px', marginBottom: '24px', border: '1px solid var(--background-modifier-border)' }}>
          <button onClick={() => setSelectedDateObj(prev => prev.minus({ days: 1 }))} style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '1.2em', padding: '0 8px' }}>‹</button>
          
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <span style={{ fontWeight: '700', fontSize: '1.1em', color: isToday ? 'var(--text-accent)' : 'var(--text-normal)' }}>{selectedDateObj.toFormat('EEEE, MMM dd')}</span>
            {!isToday && <span onClick={() => setSelectedDateObj(dc.luxon.DateTime.now().startOf('day'))} style={{ fontSize: '0.7em', color: 'var(--text-muted)', cursor: 'pointer', textDecoration: 'underline', marginTop: '2px' }}>Back to Today</span>}
          </div>

          <button onClick={() => setSelectedDateObj(prev => prev.plus({ days: 1 }))} style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', fontSize: '1.2em', padding: '0 8px' }}>›</button>
        </div>

        {/* Habit List */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '24px', borderLeft: '2px solid var(--background-modifier-border)', marginLeft: '8px', paddingLeft: '16px' }}>
          {CATEGORIES.map(category => {
            const categoryHabits = visibleHabits.filter(h => h.category === category);
            if (categoryHabits.length === 0) return null;

            // Fetch prayer time mapping Shuruq to Sunrise
            const timeKey = category === 'Shuruq' ? 'Sunrise' : category;
            const timeString = prayerTimes && prayerTimes[timeKey] ? prayerTimes[timeKey] : '';

            return (
              <div key={category} ref={el => categoryRefs.current[category] = el} style={{ position: 'relative', scrollMarginTop: '60px' }}>
                <div style={{ position: 'absolute', left: '-23px', top: '4px', width: '12px', height: '12px', borderRadius: '50%', backgroundColor: 'var(--interactive-accent)', border: '2px solid var(--background-primary)' }} />
                <h3 style={{ color: 'var(--text-accent)', margin: '0 0 12px 0', fontSize: '1.1em', fontWeight: '700', display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                    <span>{category}</span>
                    {timeString && <span style={{ fontSize: '0.75em', color: 'var(--text-muted)', fontWeight: '600' }}>{timeString}</span>}
                </h3>
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  {categoryHabits.map(habit => (
                    <HabitCardRow
                      key={habit.id} habit={habit} dayData={dayData}
                      toggleHabit={toggleHabit} incrementDuration={incrementDuration} 
                      activeQuickAdd={activeQuickAdd} setActiveQuickAdd={setActiveQuickAdd}
                      showItemSettings={showItemSettings} onEdit={setEditingHabit} onMove={moveHabit} onToggleSleep={toggleHabitSleep}
                    />
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  const renderEditForm = () => {
    const isNew = !editingHabit.id;
    const inputStyle = { width: '100%', padding: '12px', marginTop: '6px', borderRadius: '6px', border: '1px solid var(--background-modifier-border)', backgroundColor: 'var(--background-secondary)', boxSizing: 'border-box', minHeight: '44px', color: 'var(--text-normal)' };
    
    return (
      <div style={{ padding: '24px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
           <h3 style={{ margin: 0, color: 'var(--text-normal)' }}>{isNew ? 'Create New Habit' : 'Focus Mode / Edit Settings'}</h3>
           <button onClick={() => setEditingHabit(null)} style={{ background: 'var(--background-modifier-border)', border: 'none', color: 'var(--text-normal)', cursor: 'pointer', fontWeight: 'bold', fontSize: '1em', borderRadius: '50%', width: '30px', height: '30px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>✕</button>
        </div>

        <form onSubmit={handleSaveHabit} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <label style={{ fontWeight: 'bold' }}>Label: 
            <input required value={editingHabit.label || ''} onChange={e => setEditingHabit({...editingHabit, label: e.target.value})} style={inputStyle} />
          </label>
          
          <div style={{ display: 'flex', gap: '20px' }}>
            <label style={{ flex: 1, fontWeight: 'bold' }}>Timeline: 
              <select value={editingHabit.category || 'Anytime'} onChange={e => setEditingHabit({...editingHabit, category: e.target.value})} style={inputStyle}>
                {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </label>
            <label style={{ flex: 1, fontWeight: 'bold' }}>Type (Color):
              <select value={editingHabit.type || 'dua'} onChange={e => setEditingHabit({...editingHabit, type: e.target.value})} style={inputStyle}>
                {Object.keys(TYPE_COLORS).map(t => <option key={t} value={t} style={{textTransform: 'capitalize'}}>{t}</option>)}
              </select>
            </label>
          </div>
          
          <div style={{ display: 'flex', gap: '20px' }}>
            <label style={{ flex: 1, fontWeight: 'bold' }}>Schedule Type: 
              <select value={editingHabit.frequencyType || 'daily'} onChange={e => setEditingHabit({...editingHabit, frequencyType: e.target.value})} style={inputStyle}>
                <option value="daily">Every Day</option>
                <option value="weekly">Specific Days</option>
                <option value="interval">Custom Interval</option>
              </select>
            </label>
            <label style={{ flex: 1, fontWeight: 'bold' }}>Status:
              <select value={editingHabit.status || 'active'} onChange={e => setEditingHabit({...editingHabit, status: e.target.value})} style={inputStyle}>
                <option value="active">Active</option><option value="sleeping">Sleeping</option>
              </select>
            </label>
          </div>

          {(editingHabit.frequencyType === 'weekly') && (
            <div style={{ display: 'flex', gap: '8px', padding: '8px', backgroundColor: 'var(--background-secondary)', borderRadius: '8px', border: '1px solid var(--background-modifier-border)' }}>
                {WEEKDAYS.map(day => {
                    const isSelected = (editingHabit.recurringDays || []).includes(day.v);
                    return (
                        <div key={day.v} onClick={() => {
                            const cur = editingHabit.recurringDays || [];
                            setEditingHabit({...editingHabit, recurringDays: isSelected ? cur.filter(d => d !== day.v) : [...cur, day.v]});
                        }} style={{ flex: 1, textAlign: 'center', padding: '10px 0', borderRadius: '6px', backgroundColor: isSelected ? 'var(--interactive-accent)' : 'transparent', color: isSelected ? 'var(--text-on-accent)' : 'var(--text-muted)', cursor: 'pointer', fontWeight: 'bold' }}>
                            {day.l}
                        </div>
                    );
                })}
            </div>
          )}

          {(editingHabit.frequencyType === 'interval') && (
            <div style={{ display: 'flex', gap: '20px' }}>
              <label style={{ flex: 1, fontWeight: 'bold' }}>Every X Days:
                <input type="number" min="1" value={editingHabit.intervalDays || ''} onChange={e => setEditingHabit({...editingHabit, intervalDays: parseInt(e.target.value)})} style={inputStyle} placeholder="e.g. 3" />
              </label>
              <label style={{ flex: 1, fontWeight: 'bold' }}>Starting From:
                <input type="date" value={editingHabit.intervalStart || ''} onChange={e => setEditingHabit({...editingHabit, intervalStart: e.target.value})} style={inputStyle} />
              </label>
            </div>
          )}
          
          <div style={{ display: 'flex', gap: '20px' }}>
            <label style={{ flex: 1, fontWeight: 'bold' }}>Default Value: 
                <input type="number" value={editingHabit.defaultDuration || ''} onChange={e => setEditingHabit({...editingHabit, defaultDuration: parseInt(e.target.value) || null})} style={inputStyle} />
            </label>
            <label style={{ flex: 1, fontWeight: 'bold' }}>Unit: 
                <input value={editingHabit.unit || ''} onChange={e => setEditingHabit({...editingHabit, unit: e.target.value})} style={inputStyle} />
            </label>
            <label style={{ flex: 1, fontWeight: 'bold' }}>Increment Step: 
                <input type="number" min="1" value={editingHabit.step || ''} onChange={e => setEditingHabit({...editingHabit, step: parseInt(e.target.value) || null})} style={inputStyle} placeholder="e.g. 25" />
            </label>
          </div>
          
          <div style={{ display: 'flex', gap: '12px', marginTop: '8px' }}>
            {!isNew && (
              <button type="button" onClick={() => { deleteHabit(editingHabit.id); setEditingHabit(null); }} style={{ flex: 1, padding: '14px', borderRadius: '6px', border: '1px solid var(--text-error)', color: 'var(--text-error)', backgroundColor: 'var(--background-secondary)', cursor: 'pointer', fontWeight: 'bold' }}>
                Delete Habit
              </button>
            )}
            <button type="submit" style={{ flex: 1, padding: '14px', backgroundColor: 'var(--interactive-accent)', color: 'var(--text-on-accent)', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold', fontSize: '1.05em' }}>Save Changes</button>
          </div>
        </form>
      </div>
    );
  };

  return (
    <div style={{ position: 'relative', maxWidth: '100%', padding: '12px 0', fontFamily: 'var(--font-interface)' }}>
      {/* Required for quick-add animation */}
      <style>{`
        .quick-add-btn { transition: background 0.15s ease; }
        .quick-add-btn:hover { background: var(--background-modifier-hover) !important; }
        .duration-pill { transition: all 0.2s; cursor: pointer; }
        .duration-pill:hover { filter: brightness(0.9); }
      `}</style>
      
      {renderTracker()}

      {editingHabit && (
        <>
          <div onClick={() => setEditingHabit(null)} style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0, 0, 0, 0.6)', backdropFilter: 'blur(2px)', zIndex: 999 }} />
          <div style={{ position: 'fixed', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', width: '90%', maxWidth: '600px', maxHeight: '85vh', backgroundColor: 'var(--background-primary)', zIndex: 1000, borderRadius: '12px', border: '1px solid var(--background-modifier-border)', boxShadow: '0 10px 40px rgba(0,0,0,0.3)', overflowY: 'auto' }}>
            {renderEditForm()}
          </div>
        </>
      )}
    </div>
  );
}

return HabitTracker;
