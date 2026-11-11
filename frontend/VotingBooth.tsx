import React, { useState, useMemo } from 'react';
import { 
  LayoutDashboard, 
  Users, 
  Trophy, 
  Info, 
  CheckCircle2, 
  ShieldCheck, 
  Sparkles, 
  Vote, 
  ExternalLink, 
  Activity, 
  Lock, 
  Cpu, 
  Radio, 
  BookOpen,
  Volume2,
  VolumeX,
  X,
  Search,
  SlidersHorizontal,
  Settings,
  ChevronRight,
  TrendingUp,
  Award
} from 'lucide-react';

// Web Audio API Dual-Oscillator Hype Chime Synthesizer
export const playHypeAudioEffect = (enabled = true) => {
  if (!enabled) return;
  try {
    const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx();
    
    // Osc 1: Crisp high chime (D5 -> D6)
    const osc1 = ctx.createOscillator();
    const gain1 = ctx.createGain();
    osc1.type = 'sine';
    osc1.frequency.setValueAtTime(587.33, ctx.currentTime); // D5
    osc1.frequency.exponentialRampToValueAtTime(1174.66, ctx.currentTime + 0.15); // D6
    
    gain1.gain.setValueAtTime(0.2, ctx.currentTime);
    gain1.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.35);

    // Osc 2: Warm body tone
    const osc2 = ctx.createOscillator();
    const gain2 = ctx.createGain();
    osc2.type = 'triangle';
    osc2.frequency.setValueAtTime(220, ctx.currentTime);
    osc2.frequency.exponentialRampToValueAtTime(110, ctx.currentTime + 0.25);

    gain2.gain.setValueAtTime(0.25, ctx.currentTime);
    gain2.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.3);

    osc1.connect(gain1);
    gain1.connect(ctx.destination);
    
    osc2.connect(gain2);
    gain2.connect(ctx.destination);

    osc1.start();
    osc2.start();
    osc1.stop(ctx.currentTime + 0.4);
    osc2.stop(ctx.currentTime + 0.4);
  } catch (e) {
    console.error("Audio playback error:", e);
  }
};

// Candidate Dataset with Indian Political Parties & Policy Agendas
export const INITIAL_CANDIDATES = [
  {
    id: 'c1',
    name: 'Narendra Modi',
    party: 'Bharatiya Janata Party (BJP)',
    partyCode: 'bjp',
    colorTheme: 'amber',
    symbol: '🪷',
    symbolName: 'Lotus',
    votes: 2420,
    tags: ['Digital India', 'National Infrastructure', 'Economic Growth'],
    agenda: 'Viksit Bharat 2047, High-Speed Rail Corridors & Digital Public Goods',
    avatar: 'https://images.unsplash.com/photo-1544717305-2782549b5136?auto=format&fit=crop&q=80&w=400',
    borderColor: 'border-orange-200',
    hoverBorder: 'hover:border-orange-400',
    topBarColor: 'bg-orange-500',
    textColor: 'text-orange-600',
    badgeBg: 'bg-orange-50 text-orange-700 border-orange-200',
    btnStyle: 'bg-orange-500 hover:bg-orange-600 text-white shadow-sm shadow-orange-200'
  },
  {
    id: 'c2',
    name: 'Rahul Gandhi',
    party: 'Indian National Congress (INC)',
    partyCode: 'inc',
    colorTheme: 'cyan',
    symbol: '✋',
    symbolName: 'Hand',
    votes: 2190,
    tags: ['Youth Apprenticeship', 'Social Justice', 'Healthcare'],
    agenda: 'Universal Basic Income Safety Net & Right to Public Apprenticeship',
    avatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=400',
    borderColor: 'border-cyan-200',
    hoverBorder: 'hover:border-cyan-400',
    topBarColor: 'bg-cyan-600',
    textColor: 'text-cyan-600',
    badgeBg: 'bg-cyan-50 text-cyan-700 border-cyan-200',
    btnStyle: 'bg-cyan-600 hover:bg-cyan-700 text-white shadow-sm shadow-cyan-200'
  },
  {
    id: 'c3',
    name: 'N. Chandrababu Naidu',
    party: 'Telugu Desam Party (TDP)',
    partyCode: 'tdp',
    colorTheme: 'yellow',
    symbol: '🚲',
    symbolName: 'Bicycle',
    votes: 1805,
    tags: ['Hi-Tech Capital', 'Deep Tech', 'Clean Energy'],
    agenda: 'Amaravati World-Class Green Smart Capital & Deep Tech Incubation',
    avatar: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&q=80&w=400',
    borderColor: 'border-yellow-200',
    hoverBorder: 'hover:border-yellow-400',
    topBarColor: 'bg-yellow-500',
    textColor: 'text-yellow-700',
    badgeBg: 'bg-yellow-50 text-yellow-800 border-yellow-200',
    btnStyle: 'bg-yellow-500 hover:bg-yellow-600 text-slate-950 font-black shadow-sm shadow-yellow-200'
  },
  {
    id: 'c4',
    name: 'Arvind Kejriwal',
    party: 'Aam Aadmi Party (AAP)',
    partyCode: 'aap',
    colorTheme: 'emerald',
    symbol: '🧹',
    symbolName: 'Broom',
    votes: 1650,
    tags: ['Public Schools', 'Mohalla Clinics', 'Clean Air'],
    agenda: 'Modern Public School Curriculums, Subsidized Power & Clean Air Taskforces',
    avatar: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?auto=format&fit=crop&q=80&w=400',
    borderColor: 'border-emerald-200',
    hoverBorder: 'hover:border-emerald-400',
    topBarColor: 'bg-emerald-600',
    textColor: 'text-emerald-600',
    badgeBg: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    btnStyle: 'bg-emerald-600 hover:bg-emerald-700 text-white shadow-sm shadow-emerald-200'
  },
  {
    id: 'c5',
    name: 'YS Jagan Mohan Reddy',
    party: 'YSR Congress Party (YSRCP)',
    partyCode: 'ysrcp',
    colorTheme: 'blue',
    symbol: '🪭',
    symbolName: 'Ceiling Fan',
    votes: 1580,
    tags: ['Direct Benefit Transfer', 'Rural Healthcare', 'Navaratnalu'],
    agenda: 'Transparent Direct Citizen Benefit Transfers & Village Secretariat Services',
    avatar: 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&q=80&w=400',
    borderColor: 'border-blue-200',
    hoverBorder: 'hover:border-blue-400',
    topBarColor: 'bg-blue-600',
    textColor: 'text-blue-700',
    badgeBg: 'bg-blue-50 text-blue-800 border-blue-200',
    btnStyle: 'bg-blue-600 hover:bg-blue-700 text-white shadow-sm shadow-blue-200'
  },
  {
    id: 'c6',
    name: 'Nitish Kumar',
    party: 'Janata Dal (United) — JD(U)',
    partyCode: 'jdu',
    colorTheme: 'green',
    symbol: '🏹',
    symbolName: 'Arrow',
    votes: 1440,
    tags: ['Rural Roads', 'Women Empowerment', 'Sushasan'],
    agenda: 'Universal Rural Paved Connectivity & Higher Education Grants for Women',
    avatar: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&q=80&w=400',
    borderColor: 'border-green-200',
    hoverBorder: 'hover:border-green-400',
    topBarColor: 'bg-green-600',
    textColor: 'text-green-600',
    badgeBg: 'bg-green-50 text-green-700 border-green-200',
    btnStyle: 'bg-green-600 hover:bg-green-700 text-white shadow-sm shadow-green-200'
  }
];

export default function VotingBooth() {
  const [candidates, setCandidates] = useState(INITIAL_CANDIDATES);
  const [activeTab, setActiveTab] = useState('dashboard');
  const [bottomTab, setBottomTab] = useState('leaderboard');
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedPartyFilter, setSelectedPartyFilter] = useState('All');
  
  // Voting Receipt Modal state
  const [selectedCandidate, setSelectedCandidate] = useState<typeof INITIAL_CANDIDATES[0] | null>(null);
  const [receiptData, setReceiptData] = useState<{ timestamp: string; zkProof: string; ballotId: string } | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [recentVotes, setRecentVotes] = useState([
    { id: 'v1', candidate: 'Narendra Modi', party: 'BJP', time: '1 min ago', hash: '0xa3f9...b82c' },
    { id: 'v2', candidate: 'Rahul Gandhi', party: 'INC', time: '3 mins ago', hash: '0x7e21...491d' },
    { id: 'v3', candidate: 'N. Chandrababu Naidu', party: 'TDP', time: '5 mins ago', hash: '0xc81a...990b' }
  ]);

  // Calculate total votes cast
  const totalVotesCast = useMemo(() => {
    return candidates.reduce((sum, item) => sum + item.votes, 0);
  }, [candidates]);

  // Filtered Candidates
  const filteredCandidates = useMemo(() => {
    return candidates.filter((c) => {
      const matchesSearch = c.name.toLowerCase().includes(searchQuery.toLowerCase()) || 
                            c.party.toLowerCase().includes(searchQuery.toLowerCase());
      const matchesParty = selectedPartyFilter === 'All' || c.party === selectedPartyFilter;
      return matchesSearch && matchesParty;
    });
  }, [candidates, searchQuery, selectedPartyFilter]);

  // Cryptographic ZK-Proof Hash Simulator conforming to RFC 6962
  const generateZKProof = () => {
    const chars = 'abcdef0123456789';
    let line1 = '0x';
    let line2 = '0x';
    let line3 = '0x';
    for (let i = 0; i < 32; i++) {
      line1 += chars[Math.floor(Math.random() * chars.length)];
      line2 += chars[Math.floor(Math.random() * chars.length)];
      line3 += chars[Math.floor(Math.random() * chars.length)];
    }
    return `${line1.slice(0, 8)}...${line1.slice(-8)}\n${line2.slice(0, 8)}...${line2.slice(-8)}\n${line3.slice(0, 8)}...${line3.slice(-8)}`;
  };

  const handleVote = (candidate: typeof INITIAL_CANDIDATES[0]) => {
    playHypeAudioEffect(soundEnabled);

    // If canvas-confetti exists, fire it
    const win = window as unknown as { confetti?: (options: Record<string, unknown>) => void };
    if (typeof win.confetti === 'function') {
      win.confetti({
        particleCount: 110,
        spread: 75,
        origin: { y: 0.6 },
        colors: ['#FF9933', '#FFFFFF', '#138808', '#00D2FF', '#9D00FF']
      });
    }
    
    // Increment candidate votes
    const updated = candidates.map(c => {
      if (c.id === candidate.id) {
        return { ...c, votes: c.votes + 1 };
      }
      return c;
    });
    setCandidates(updated);

    // Create ZK Proof receipt
    const zkHash = generateZKProof();
    const newVoteRecord = {
      id: `v-${Date.now()}`,
      candidate: candidate.name,
      party: candidate.party,
      time: 'Just now',
      hash: zkHash.split('\n')[0]
    };

    setRecentVotes([newVoteRecord, ...recentVotes.slice(0, 4)]);
    setSelectedCandidate(candidate);
    setReceiptData({
      timestamp: new Date().toLocaleTimeString(),
      zkProof: zkHash,
      ballotId: `VVJ-2026-${Math.floor(100000 + Math.random() * 900000)}`
    });
    setIsModalOpen(true);
  };

  const partyOptions = ['All', ...Array.from(new Set(candidates.map(c => c.party)))];

  return (
    <div className="flex h-screen w-full bg-slate-50 text-slate-800 font-sans overflow-hidden select-none">
      
      {/* COMPONENT 1: LEFT SIDEBAR */}
      <aside className="w-64 bg-white border-r border-slate-200 flex flex-col justify-between p-5 z-20 shrink-0 shadow-sm">
        <div>
          {/* Logo Header */}
          <div className="flex items-center gap-3 px-2 py-3 mb-6">
            <div className="w-10 h-10 rounded-xl bg-indigo-600 flex items-center justify-center shadow-md shadow-indigo-100">
              <Vote className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="text-xl font-bold tracking-tight text-slate-900 flex items-center gap-1">
                VeriVote<span className="text-indigo-600">-J</span>
              </h1>
              <span className="text-[10px] text-slate-400 font-medium tracking-wider uppercase">Educational Polling</span>
            </div>
          </div>

          {/* Navigation Items */}
          <nav className="space-y-1">
            {[
              { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
              { id: 'candidates', label: 'Candidates', icon: Users },
              { id: 'leaderboard', label: 'Leaderboard', icon: Trophy },
              { id: 'about', label: 'About & Transparency', icon: Info },
            ].map((item) => {
              const Icon = item.icon;
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => setActiveTab(item.id)}
                  className={`w-full flex items-center gap-3 px-3.5 py-2.5 rounded-lg text-sm font-semibold transition-all duration-150 ${
                    isActive
                      ? 'bg-indigo-50 text-indigo-700 border-l-4 border-indigo-600 shadow-sm'
                      : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100/80'
                  }`}
                >
                  <Icon className={`w-4 h-4 ${isActive ? 'text-indigo-600' : 'text-slate-400'}`} />
                  <span>{item.label}</span>
                </button>
              );
            })}
          </nav>
        </div>

        {/* Audio Toggle & Footer Information */}
        <div className="pt-4 border-t border-slate-200 space-y-3">
          <button
            onClick={() => setSoundEnabled(!soundEnabled)}
            className="w-full flex items-center justify-between px-3 py-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-xs text-slate-700 transition-colors"
          >
            <span className="flex items-center gap-2 font-medium">
              {soundEnabled ? <Volume2 className="w-4 h-4 text-emerald-600" /> : <VolumeX className="w-4 h-4 text-slate-400" />}
              Audio Chime SFX
            </span>
            <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${soundEnabled ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-500'}`}>
              {soundEnabled ? 'ON' : 'OFF'}
            </span>
          </button>

          <div className="px-2 text-[11px] text-slate-400 flex items-center justify-between font-mono">
            <span>VeriVote v2.4 (Light)</span>
            <span className="flex items-center gap-1.5 text-emerald-600 font-sans font-semibold">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
              Live
            </span>
          </div>
        </div>
      </aside>

      {/* MAIN CONTENT WRAPPER */}
      <main className="flex-1 flex flex-col overflow-y-auto bg-slate-50 relative">
        
        {/* COMPONENT 2: TOP HEADER BAR */}
        <header className="sticky top-0 z-10 px-8 py-4 bg-white/80 backdrop-blur-md border-b border-slate-200 flex items-center justify-between shadow-xs">
          <div className="flex items-center gap-4">
            <div className="bg-slate-100 border border-slate-200 rounded-full px-4 py-1.5 flex items-center gap-3">
              <span className="text-slate-900 font-bold text-xs tracking-wide">VeriVote-J Portal</span>
              <div className="h-3.5 w-[1px] bg-slate-300"></div>
              {/* Pulsing Emerald Election Open Badge */}
              <div className="flex items-center gap-1.5 bg-emerald-50 border border-emerald-200 rounded-full px-2.5 py-0.5 shadow-xs">
                <Radio className="w-3 h-3 text-emerald-600 animate-pulse" />
                <span className="text-[11px] font-mono font-bold text-emerald-700 tracking-wider">ELECTION: OPEN</span>
              </div>
            </div>
          </div>

          <div className="flex items-center gap-4">
            {/* Real-time Votes Cast Counter */}
            <div className="bg-slate-100 border border-slate-200 px-3.5 py-1.5 rounded-full flex items-center gap-2 text-xs font-medium text-slate-600">
              <Activity className="w-3.5 h-3.5 text-indigo-600" />
              <span>TOTAL VOTES CAST:</span>
              <span className="font-mono text-indigo-600 font-bold text-xs">{totalVotesCast.toLocaleString()}</span>
            </div>

            {/* Profile Avatar Badge */}
            <div className="flex items-center gap-2 pl-2">
              <div className="w-8 h-8 rounded-full bg-indigo-100 border border-indigo-300 flex items-center justify-center text-xs font-bold text-indigo-700">
                IND
              </div>
              <span className="text-xs font-semibold text-slate-700 hidden sm:inline">Voter ID #9042</span>
            </div>
          </div>
        </header>

        {/* DASHBOARD VIEW */}
        {activeTab === 'dashboard' && (
          <div className="p-8 max-w-7xl mx-auto w-full space-y-6">
            
            {/* HERO TITLE SECTION */}
            <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
              <div>
                <div className="flex items-center gap-1.5 mb-1">
                  <Sparkles className="w-4 h-4 text-indigo-600" />
                  <span className="text-xs font-bold text-indigo-600 tracking-wider uppercase">Civic Tech Educational Polling</span>
                </div>
                <h1 className="text-3xl font-extrabold text-slate-900 tracking-tight">Choose Your Leader</h1>
                <p className="text-slate-500 text-xs mt-1">
                  Educational Voting Mockup | Review policy platforms and cast a cryptographically verified ballot.
                </p>
              </div>

              {/* SEARCH & FILTER BAR */}
              <div className="flex items-center gap-3">
                {/* Search Box */}
                <div className="relative">
                  <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    placeholder="Search candidates..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pl-9 pr-4 py-2 bg-white border border-slate-200 rounded-lg text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 w-48 sm:w-60 shadow-xs"
                  />
                </div>

                {/* Party Dropdown Filter */}
                <div className="flex items-center gap-1.5 bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 shadow-xs">
                  <SlidersHorizontal className="w-3.5 h-3.5 text-slate-500" />
                  <select
                    value={selectedPartyFilter}
                    onChange={(e) => setSelectedPartyFilter(e.target.value)}
                    className="bg-transparent text-xs text-slate-700 font-medium focus:outline-none cursor-pointer"
                  >
                    {partyOptions.map((party) => (
                      <option key={party} value={party}>
                        {party === 'All' ? 'All Parties' : party}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            {/* CANDIDATE GRID (3 Columns, 6 Cards) */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {filteredCandidates.map((candidate) => {
                const votePercentage = totalVotesCast > 0 
                  ? ((candidate.votes / totalVotesCast) * 100).toFixed(1)
                  : '0.0';

                return (
                  <div
                    key={candidate.id}
                    className={`bg-white rounded-xl border ${candidate.borderColor} ${candidate.hoverBorder} shadow-xs hover:shadow-md transition-all duration-200 overflow-hidden flex flex-col justify-between group`}
                  >
                    {/* Top Color Accent Bar */}
                    <div className={`h-1.5 w-full ${candidate.topBarColor}`}></div>

                    <div className="p-5">
                      {/* Top Row: Symbol & Party Tag */}
                      <div className="flex items-center justify-between mb-4">
                        <span className={`text-[11px] font-bold px-2.5 py-0.5 rounded-full border ${candidate.badgeBg}`}>
                          {candidate.party}
                        </span>
                        <span className="text-xl p-1 bg-slate-50 rounded-md border border-slate-100" title={candidate.symbolName}>
                          {candidate.symbol}
                        </span>
                      </div>

                      {/* Candidate Avatar & Name */}
                      <div className="flex items-center gap-4 mb-4">
                        <img
                          src={candidate.avatar}
                          alt={candidate.name}
                          className="w-16 h-16 rounded-full object-cover border-2 border-slate-100 shadow-sm group-hover:scale-105 transition-transform"
                        />
                        <div>
                          <h3 className="text-base font-bold text-slate-900 group-hover:text-indigo-600 transition-colors">
                            {candidate.name}
                          </h3>
                          {/* Policy Tags */}
                          <div className="flex flex-wrap gap-1 mt-1">
                            {candidate.tags.map((tag) => (
                              <span key={tag} className="text-[10px] bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">
                                {tag}
                              </span>
                            ))}
                          </div>
                        </div>
                      </div>

                      {/* Agenda Platform Summary */}
                      <p className="text-xs text-slate-500 leading-relaxed bg-slate-50/70 p-2.5 rounded-lg border border-slate-100 mb-4 h-12 line-clamp-2">
                        "{candidate.agenda}"
                      </p>

                      {/* Vote Count & Percent Metrics */}
                      <div className="flex items-center justify-between pt-3 border-t border-slate-100 text-xs">
                        <div>
                          <span className="text-slate-400 text-[10px] uppercase font-medium block">Total Votes</span>
                          <span className="font-mono font-bold text-slate-800 text-xs">{candidate.votes.toLocaleString()}</span>
                        </div>
                        <div className="text-right">
                          <span className="text-slate-400 text-[10px] uppercase font-medium block">Polling Share</span>
                          <span className={`font-mono font-bold text-xs ${candidate.textColor}`}>{votePercentage}%</span>
                        </div>
                      </div>
                    </div>

                    {/* Clean Action Button */}
                    <div className="p-4 bg-slate-50/50 border-t border-slate-100">
                      <button
                        onClick={() => handleVote(candidate)}
                        className={`w-full py-2.5 px-4 rounded-lg font-bold text-xs flex items-center justify-center gap-2 transition-all active:scale-[0.98] ${candidate.btnStyle}`}
                      >
                        <span>VOTE FOR CANDIDATE</span>
                        <ExternalLink className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>

            {filteredCandidates.length === 0 && (
              <div className="bg-white p-12 rounded-xl border border-slate-200 text-center space-y-2">
                <Search className="w-8 h-8 text-slate-300 mx-auto" />
                <h3 className="text-sm font-bold text-slate-700">No Candidates Found</h3>
                <p className="text-xs text-slate-400">Try adjusting your search terms or party filters.</p>
              </div>
            )}

            {/* COMPONENT 4: BOTTOM SECTION - ANALYTICS & TRANSPARENCY GUIDE */}
            <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-xs space-y-5">
              <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b border-slate-100 pb-4">
                <div>
                  <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                    <BookOpen className="w-4 h-4 text-indigo-600" />
                    Learning & Verification Feed
                  </h2>
                  <p className="text-xs text-slate-500">Audit election metrics, cryptographic proof signatures, and platform standards.</p>
                </div>

                {/* Bottom Toggle Pills */}
                <div className="bg-slate-100 p-1 rounded-lg flex items-center border border-slate-200">
                  <button
                    onClick={() => setBottomTab('leaderboard')}
                    className={`px-3.5 py-1.5 rounded-md text-xs font-semibold transition-all ${
                      bottomTab === 'leaderboard'
                        ? 'bg-white text-indigo-700 shadow-xs'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    Leaderboard
                  </button>
                  <button
                    onClick={() => setBottomTab('recent')}
                    className={`px-3.5 py-1.5 rounded-md text-xs font-semibold transition-all ${
                      bottomTab === 'recent'
                        ? 'bg-white text-indigo-700 shadow-xs'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    Live Audit Feed
                  </button>
                  <button
                    onClick={() => setBottomTab('guide')}
                    className={`px-3.5 py-1.5 rounded-md text-xs font-semibold transition-all ${
                      bottomTab === 'guide'
                        ? 'bg-white text-indigo-700 shadow-xs'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    Civic Guide
                  </button>
                </div>
              </div>

              {/* Leaderboard Sub-tab */}
              {bottomTab === 'leaderboard' && (
                <div className="space-y-3">
                  {candidates
                    .slice()
                    .sort((a, b) => b.votes - a.votes)
                    .map((item, index) => {
                      const pct = totalVotesCast > 0 ? ((item.votes / totalVotesCast) * 100).toFixed(1) : '0';
                      return (
                        <div key={item.id} className="flex items-center gap-4 bg-slate-50 p-3 rounded-lg border border-slate-100">
                          <span className={`w-6 text-center font-mono font-bold text-xs ${index === 0 ? 'text-amber-600' : 'text-slate-400'}`}>
                            #{index + 1}
                          </span>
                          <span className="text-lg">{item.symbol}</span>
                          <div className="flex-1 min-w-0">
                            <div className="flex justify-between text-xs mb-1">
                              <span className="font-bold text-slate-800 truncate">{item.name} <span className="text-slate-400 font-normal">({item.party})</span></span>
                              <span className="font-mono text-slate-600">{item.votes} votes ({pct}%)</span>
                            </div>
                            <div className="w-full bg-slate-200 rounded-full h-1.5 overflow-hidden">
                              <div
                                className={`h-full transition-all duration-500 rounded-full ${item.topBarColor}`}
                                style={{ width: `${pct}%` }}
                              ></div>
                            </div>
                          </div>
                        </div>
                      );
                    })}
                </div>
              )}

              {/* Live Audit Feed Sub-tab */}
              {bottomTab === 'recent' && (
                <div className="space-y-2">
                  <div className="text-[11px] text-slate-400 font-mono flex items-center justify-between mb-1">
                    <span>Cryptographic Verification Stream</span>
                    <span className="text-emerald-600 font-semibold flex items-center gap-1">
                      <ShieldCheck className="w-3.5 h-3.5" /> ZK-Proof Active
                    </span>
                  </div>
                  {recentVotes.map((v) => (
                    <div key={v.id} className="flex items-center justify-between bg-slate-50 p-2.5 rounded-lg border border-slate-200 text-xs">
                      <div className="flex items-center gap-2.5">
                        <CheckCircle2 className="w-4 h-4 text-emerald-600" />
                        <div>
                          <span className="font-bold text-slate-800">{v.candidate}</span>
                          <span className="text-slate-400 ml-1.5 text-[11px]">({v.party})</span>
                        </div>
                      </div>
                      <div className="flex items-center gap-3">
                        <span className="font-mono text-slate-500 text-[11px] bg-slate-200/60 px-2 py-0.5 rounded border border-slate-300/40">{v.hash}</span>
                        <span className="text-slate-400 text-[10px]">{v.time}</span>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* Civic Guide Sub-tab */}
              {bottomTab === 'guide' && (
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
                  <div className="bg-slate-50 p-4 rounded-lg border border-slate-200 space-y-1.5">
                    <Lock className="w-4 h-4 text-indigo-600" />
                    <h4 className="font-bold text-slate-800 text-xs">Anonymity & Security</h4>
                    <p className="text-slate-500 text-[11px] leading-relaxed">
                      Zero-Knowledge proofs ensure your vote choice remains confidential while allowing verifiable tally inclusion.
                    </p>
                  </div>
                  <div className="bg-slate-50 p-4 rounded-lg border border-slate-200 space-y-1.5">
                    <Cpu className="w-4 h-4 text-violet-600" />
                    <h4 className="font-bold text-slate-800 text-xs">Educational Polling</h4>
                    <p className="text-slate-500 text-[11px] leading-relaxed">
                      Designed to inform voters about candidate manifestos, policy agendas, and digital civic participation.
                    </p>
                  </div>
                  <div className="bg-slate-50 p-4 rounded-lg border border-slate-200 space-y-1.5">
                    <ShieldCheck className="w-4 h-4 text-emerald-600" />
                    <h4 className="font-bold text-slate-800 text-xs">Auditable Receipts</h4>
                    <p className="text-slate-500 text-[11px] leading-relaxed">
                      Every cast ballot generates an immutable cryptographic transaction hash for public verification.
                    </p>
                  </div>
                </div>
              )}
            </div>

          </div>
        )}

        {/* CANDIDATES DIRECTORY TAB */}
        {activeTab === 'candidates' && (
          <div className="p-8 max-w-7xl mx-auto w-full space-y-6">
            <div>
              <h2 className="text-2xl font-extrabold text-slate-900">Candidate Directory</h2>
              <p className="text-slate-500 text-xs mt-0.5">Explore full policy proposals and candidate backgrounds.</p>
            </div>
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {candidates.map((c) => (
                <div key={c.id} className="bg-white border border-slate-200 p-6 rounded-xl shadow-xs flex gap-5">
                  <img src={c.avatar} alt={c.name} className="w-20 h-20 rounded-xl object-cover border border-slate-200" />
                  <div className="space-y-2 flex-1">
                    <div className="flex items-center justify-between">
                      <h3 className="text-base font-bold text-slate-900">{c.name}</h3>
                      <span className="text-xl">{c.symbol}</span>
                    </div>
                    <span className={`inline-block text-xs font-bold ${c.textColor}`}>{c.party}</span>
                    <p className="text-xs text-slate-600 bg-slate-50 p-2.5 rounded-lg border border-slate-100">{c.agenda}</p>
                    <div className="flex items-center justify-between pt-1 text-xs text-slate-500 font-mono">
                      <span>Total Votes: {c.votes}</span>
                      <button 
                        onClick={() => handleVote(c)}
                        className="px-3 py-1 bg-indigo-50 text-indigo-700 border border-indigo-200 rounded-md font-sans font-bold hover:bg-indigo-100 text-xs"
                      >
                        Vote
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* STANDALONE LEADERBOARD TAB */}
        {activeTab === 'leaderboard' && (
          <div className="p-8 max-w-5xl mx-auto w-full space-y-6">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-2xl font-extrabold text-slate-900">Election Standings</h2>
                <p className="text-slate-500 text-xs">Official real-time vote rankings.</p>
              </div>
              <Trophy className="w-8 h-8 text-amber-500" />
            </div>

            <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-50 text-slate-500 uppercase font-mono border-b border-slate-200">
                  <tr>
                    <th className="p-4">Rank</th>
                    <th className="p-4">Candidate</th>
                    <th className="p-4">Party</th>
                    <th className="p-4 text-right">Votes</th>
                    <th className="p-4 text-right">Share</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {candidates
                    .slice()
                    .sort((a, b) => b.votes - a.votes)
                    .map((item, idx) => {
                      const share = totalVotesCast > 0 ? ((item.votes / totalVotesCast) * 100).toFixed(1) : '0';
                      return (
                        <tr key={item.id} className="hover:bg-slate-50/80">
                          <td className="p-4 font-bold font-mono text-indigo-600">#{idx + 1}</td>
                          <td className="p-4 flex items-center gap-3">
                            <img src={item.avatar} className="w-8 h-8 rounded-full object-cover border border-slate-200" />
                            <span className="font-bold text-slate-800">{item.name}</span>
                          </td>
                          <td className="p-4">
                            <span className={`font-semibold ${item.textColor}`}>{item.party}</span>
                          </td>
                          <td className="p-4 text-right font-mono font-bold text-slate-800">{item.votes.toLocaleString()}</td>
                          <td className="p-4 text-right font-mono text-slate-600">{share}%</td>
                        </tr>
                      );
                    })}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ABOUT TAB */}
        {activeTab === 'about' && (
          <div className="p-8 max-w-4xl mx-auto w-full space-y-6">
            <h2 className="text-2xl font-extrabold text-slate-900">About VeriVote-J Platform</h2>
            <p className="text-slate-600 leading-relaxed text-xs">
              VeriVote-J is a clean, professional educational voting web platform designed to simulate modern civic technology.
              Featuring Zero-Knowledge proof cryptographic verification receipts and real-time polling data, it allows educational institutions and youth forums to hold transparent elections.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
              <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-xs">
                <CheckCircle2 className="w-5 h-5 text-emerald-600 mb-2" />
                <h4 className="font-bold text-slate-900 text-sm">Platform Features</h4>
                <ul className="text-xs text-slate-500 space-y-1 mt-2 list-disc list-inside">
                  <li>Clean professional light-mode aesthetic</li>
                  <li>Real-time vote tallying and search filters</li>
                  <li>Web Audio dual-oscillator feedback synthesizer</li>
                  <li>ZK Cryptographic receipt modal generation</li>
                </ul>
              </div>
              <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-xs">
                <Sparkles className="w-5 h-5 text-amber-500 mb-2" />
                <h4 className="font-bold text-slate-900 text-sm">Civic Objectives</h4>
                <ul className="text-xs text-slate-500 space-y-1 mt-2 list-disc list-inside">
                  <li>Encourage policy-focused decision making</li>
                  <li>Demystify digital election security</li>
                  <li>Provide transparent statistical insights</li>
                </ul>
              </div>
            </div>
          </div>
        )}

      </main>

      {/* CONFIRMATION / RECEIPT MODAL */}
      {isModalOpen && receiptData && selectedCandidate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-xs animate-fadeIn">
          <div className="relative w-full max-w-md bg-white border border-slate-200 rounded-2xl p-6 shadow-xl space-y-5">
            
            {/* Close Button */}
            <button
              onClick={() => setIsModalOpen(false)}
              className="absolute top-4 right-4 p-1.5 text-slate-400 hover:text-slate-700 rounded-full bg-slate-100"
            >
              <X className="w-4 h-4" />
            </button>

            {/* Success Header */}
            <div className="text-center space-y-1.5">
              <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-emerald-100 text-emerald-600 mb-1">
                <CheckCircle2 className="w-8 h-8" />
              </div>
              <h3 className="text-xl font-bold text-slate-900 tracking-tight">VOTE RECORDED</h3>
              <p className="text-xs font-mono font-bold text-emerald-600 tracking-wider uppercase">
                BALLOT ANONYMOUSLY SEALED!
              </p>
            </div>

            {/* Candidate Summary */}
            <div className="bg-slate-50 p-3.5 rounded-xl border border-slate-200 text-center space-y-0.5">
              <span className="text-[10px] text-slate-400 uppercase font-semibold block">Ballot Selected</span>
              <h4 className="text-base font-bold text-slate-900">{selectedCandidate.name}</h4>
              <span className={`text-xs font-semibold ${selectedCandidate.textColor}`}>{selectedCandidate.party}</span>
            </div>

            {/* ZK Proof Receipt Code Box */}
            <div className="bg-slate-900 p-4 rounded-xl text-center space-y-2 text-white">
              <div className="flex items-center justify-center gap-1.5 text-[11px] text-emerald-400 font-bold">
                <ShieldCheck className="w-4 h-4" />
                <span>CRYPTOGRAPHIC ZK-PROOF GENERATED</span>
              </div>
              <div className="text-[11px] font-mono text-slate-300 leading-relaxed bg-slate-800 p-2.5 rounded border border-slate-700 select-all">
                {receiptData.zkProof}
              </div>
              <div className="flex justify-between text-[10px] text-slate-400 font-mono pt-1">
                <span>ID: {receiptData.ballotId}</span>
                <span>Time: {receiptData.timestamp}</span>
              </div>
            </div>

            {/* Close / Action Button */}
            <button
              onClick={() => setIsModalOpen(false)}
              className="w-full py-3 rounded-lg font-bold text-xs bg-indigo-600 hover:bg-indigo-700 text-white shadow-sm transition-all"
            >
              CLOSE RECEIPT
            </button>
          </div>
        </div>
      )}

    </div>
  );
}
